# --------------------------------------------------------------
# This module is used to generate the positioning estimations
# using the Maximum Likelihood - Particle Swarm Optimization Cooperative Localization method (PySwarms integration).
# The DB loading, loads the initial positions from ARLCL results,
# to have a common initialization (could be disabled).
# --------------------------------------------------------------
# TODO: Allow user to set custom model parameters
import math
import abc
import os
import pprint
import shutil
import sys
import zipfile
import logging
import logging.config
import inspect

import numpy as np
from tqdm import trange
from copy import copy
from collections import deque, namedtuple
from attr import attrib, attrs
from attr.validators import instance_of

verbose_logging = False

measurement = {"ble": {"unit": "RSS", "db_ext": ".rss"},
               "uwb": {"unit": "TIME", "db_ext": ".smpl"}}

args = sys.argv
str_params = {}
ARLCL_zipped_scenario_path = None

# Parse all parameters and put the in the corresponding dictionary in unconstrained order
for parameter in sys.argv[1:]:
    key_value_pair = parameter.split("=")
    str_params[key_value_pair[0]] = key_value_pair[1]

ML_PSO_export_path = str_params["out"]
zipped_arlcl_results_path = str_params["arlcl_out"]
DB_path = str_params["db"]
scenarios_path = str_params["batch"]
input_log_path = str_params["log"]
model = str_params["model"]
scenario_idx = int(str_params["scenario_id"])
input_seed = int(str_params["seed"])
last_evaluation_id = int(str_params["end_iter"])
optimization_iterations = int(str_params["opts"])
particles = int(str_params["particles"])
c1_arg = float(str_params["c1"])
c2_arg = float(str_params["c2"])
w_arg = float(str_params["w"])

np.random.seed(input_seed)


# This definition is required before Reporter because the latter's dependency
def get_cur_scenario(eval_scenarios, eval_scenario_id):
    with open(eval_scenarios) as fp:
        for i, line in enumerate(fp):
            if i == eval_scenario_id:
                split_line = line.replace("\n", "").split(" ")
                filename = split_line[0] + "_" + split_line[1].replace("(", "").replace(")", "") + "_" + split_line[2]
                break
    return filename


# Use the provided ID to identify the scenario
# TODO: One can implement his own way of loading the scenario. Current approach is used for the needs of SLURM cluster
scenario = get_cur_scenario(scenarios_path, scenario_idx)


# ONE CAN EVEN UNCOMMENT THIS TO SET MANUALLY A SCENARIO
# scenario = "A_1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40_5"


class Reporter(object):
    def __init__(self, logger=None):
        self.logger = logger or logging.getLogger(__name__)
        self.printer = pprint.PrettyPrinter()

        # Can be used to separate logs at iteration granularity
        # current_time = datetime.now()
        # filename_timestamp = current_time.strftime("%Y-%m-%d_%H-%M-%S")
        # self.log_path = os.path.join(input_log_path, scenario + "_" + filename_timestamp + ".log")

        self.log_path = os.path.join(input_log_path, scenario + ".log")
        self._bar_fmt = "{l_bar}{bar}|{n_fmt}/{total_fmt}{postfix}"
        self._env_key = "LOG_CFG"
        self._default_config = {
            "version": 1,
            "disable_existing_loggers": False,
            "formatters": {
                "standard": {
                    "format": "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
                }
            },
            "handlers": {
                "default": {
                    "level": "INFO",
                    "class": "logging.StreamHandler",
                    "formatter": "standard",
                },
                "file_default": {
                    "level": "INFO",
                    "formatter": "standard",
                    "class": "logging.handlers.RotatingFileHandler",
                    "filename": self.log_path,
                    "encoding": "utf8",
                    "maxBytes": 10485760,
                    "backupCount": 20,
                },
            },
            "loggers": {
                "": {
                    "handlers": ["default", "file_default"],
                    "level": "INFO",
                    "propagate": True,
                }
            },
        }
        self._load_defaults()

    def log(self, msg, lvl=logging.INFO, *args, **kwargs):
        self.logger.log(lvl, msg, *args, **kwargs)

    def print(self, msg, verbosity, threshold=0):
        if verbosity > threshold:
            self.printer.pprint(msg)
        else:
            pass

    def _load_defaults(self):
        """Load default logging configuration"""
        logging.config.dictConfig(self._default_config)

    def pbar(self, iters, desc=None):
        self.t = trange(iters, desc=desc, bar_format=self._bar_fmt)
        return self.t

    def hook(self, *args, **kwargs):
        self.t.set_postfix(*args, **kwargs)


rep = Reporter()


class HandlerMixin(object):
    def _merge_dicts(self, *dict_args):
        """Backward-compatible helper method to combine two dicts"""
        result = {}
        for dictionary in dict_args:
            result.update(dictionary)
        return result

    def _out_of_bounds(self, position, bounds):
        """Helper method to find indices of out-of-bound positions

        This method finds the indices of the particles that are out-of-bound.
        """
        lb, ub = bounds
        greater_than_bound = np.nonzero(position > ub)
        lower_than_bound = np.nonzero(position < lb)
        return (lower_than_bound, greater_than_bound)

    def _get_all_strategies(self):
        """Helper method to automatically generate a dict of strategies"""
        return {
            k: v
            for k, v in inspect.getmembers(self, predicate=inspect.isroutine)
            if not k.startswith(("__", "_"))
        }


class VelocityHandler(HandlerMixin):
    def __init__(self, strategy):
        self.strategy = strategy
        self.strategies = self._get_all_strategies()
        self.rep = Reporter(logger=logging.getLogger(__name__))
        self.memory = None

    def __call__(self, velocity, clamp, **kwargs):
        try:
            new_position = self.strategies[self.strategy](
                velocity, clamp, **kwargs
            )
        except KeyError:
            message = "Unrecognized strategy: {}. Choose one among: " + str(
                [strat for strat in self.strategies.keys()]
            )
            self.rep.logger.exception(message.format(self.strategy))
            raise
        else:
            return new_position

    def __apply_clamp(self, velocity, clamp):
        """Helper method to apply a clamp to a velocity vector"""
        clamped_vel = velocity
        min_velocity, max_velocity = clamp
        lower_than_clamp = clamped_vel <= min_velocity
        greater_than_clamp = clamped_vel >= max_velocity
        clamped_vel = np.where(lower_than_clamp, min_velocity, clamped_vel)
        clamped_vel = np.where(greater_than_clamp, max_velocity, clamped_vel)
        return clamped_vel

    def unmodified(self, velocity, clamp=None, **kwargs):
        """Leaves the velocity unchanged"""
        if clamp is None:
            new_vel = velocity
        else:
            if clamp is not None:
                new_vel = self.__apply_clamp(velocity, clamp)
        return new_vel

    def adjust(self, velocity, clamp=None, **kwargs):
        try:
            if self.memory is None:
                new_vel = velocity
                self.memory = kwargs["position"]
            else:
                new_vel = kwargs["position"] - self.memory
                self.memory = kwargs["position"]
                if clamp is not None:
                    new_vel = self.__apply_clamp(new_vel, clamp)
        except KeyError:
            self.rep.logger.exception("Keyword 'position' missing")
            raise
        else:
            return new_vel

    def invert(self, velocity, clamp=None, **kwargs):
        try:
            # Default for the shrinking factor
            if "z" not in kwargs:
                z = 0.5
            else:
                z = kwargs["z"]
            lower_than_bound, greater_than_bound = self._out_of_bounds(
                kwargs["position"], kwargs["bounds"]
            )
            new_vel = velocity
            new_vel[lower_than_bound[0]] = (-z) * new_vel[lower_than_bound[0]]
            new_vel[greater_than_bound[0]] = (-z) * new_vel[
                greater_than_bound[0]
            ]
            if clamp is not None:
                new_vel = self.__apply_clamp(new_vel, clamp)
        except KeyError:
            self.rep.logger.exception("Keyword 'position' or 'bounds' missing")
            raise
        else:
            return new_vel

    def zero(self, velocity, clamp=None, **kwargs):
        """Set velocity to zero if the particle is out of bounds"""
        try:
            lower_than_bound, greater_than_bound = self._out_of_bounds(
                kwargs["position"], kwargs["bounds"]
            )
            new_vel = velocity
            new_vel[lower_than_bound[0]] = np.zeros(velocity.shape[1])
            new_vel[greater_than_bound[0]] = np.zeros(velocity.shape[1])
        except KeyError:
            self.rep.logger.exception("Keyword 'position' or 'bounds' missing")
            raise
        else:
            return new_vel


class BoundaryHandler(HandlerMixin):
    def __init__(self, strategy):
        self.strategy = strategy
        self.strategies = self._get_all_strategies()
        self.rep = Reporter(logger=logging.getLogger(__name__))
        self.memory = None

    def __call__(self, position, bounds, **kwargs):
        try:
            new_position = self.strategies[self.strategy](
                position, bounds, **kwargs
            )
        except KeyError:
            message = "Unrecognized strategy: {}. Choose one among: " + str(
                [strat for strat in self.strategies.keys()]
            )
            self.rep.logger.exception(message.format(self.strategy))
            raise
        else:
            return new_position

    def nearest(self, position, bounds, **kwargs):
        lb, ub = bounds
        bool_greater = position > ub
        bool_lower = position < lb
        new_pos = np.where(bool_lower, lb, position)
        new_pos = np.where(bool_greater, ub, new_pos)
        return new_pos

    def reflective(self, position, bounds, **kwargs):
        lb, ub = bounds
        lower_than_bound, greater_than_bound = self._out_of_bounds(
            position, bounds
        )
        new_pos = position
        while lower_than_bound[0].size != 0 or greater_than_bound[0].size != 0:
            if lower_than_bound[0].size > 0:
                new_pos[lower_than_bound] = (
                        2 * lb[lower_than_bound[1]] - new_pos[lower_than_bound]
                )
            if greater_than_bound[0].size > 0:
                new_pos[greater_than_bound] = (
                        2 * ub[greater_than_bound[1]] - new_pos[greater_than_bound]
                )
            lower_than_bound, greater_than_bound = self._out_of_bounds(
                new_pos, bounds
            )

        return new_pos

    def shrink(self, position, bounds, **kwargs):
        if self.memory is None:
            new_pos = position
            self.memory = new_pos
        else:
            lb, ub = bounds
            lower_than_bound, greater_than_bound = self._out_of_bounds(
                position, bounds
            )
            velocity = position - self.memory
            # Create a coefficient matrix
            sigma = np.tile(1.0, position.shape)
            sigma[lower_than_bound] = (
                                              lb[lower_than_bound[1]] - self.memory[lower_than_bound]
                                      ) / velocity[lower_than_bound]
            sigma[greater_than_bound] = (
                                                ub[greater_than_bound[1]] - self.memory[greater_than_bound]
                                        ) / velocity[greater_than_bound]
            min_sigma = np.amin(sigma, axis=1)
            new_pos = position
            new_pos[lower_than_bound[0]] = (
                    self.memory[lower_than_bound[0]]
                    + np.multiply(
                min_sigma[lower_than_bound[0]],
                velocity[lower_than_bound[0]].T,
            ).T
            )
            new_pos[greater_than_bound[0]] = (
                    self.memory[greater_than_bound[0]]
                    + np.multiply(
                min_sigma[greater_than_bound[0]],
                velocity[greater_than_bound[0]].T,
            ).T
            )
            self.memory = new_pos
        return new_pos

    def random(self, position, bounds, **kwargs):
        lb, ub = bounds
        lower_than_bound, greater_than_bound = self._out_of_bounds(
            position, bounds
        )
        # Set indices that are greater than bounds
        new_pos = position
        new_pos[greater_than_bound[0]] = np.array(
            [
                np.array([u - l for u, l in zip(ub, lb)])
                * np.random.random_sample((position.shape[1],))
                + lb
            ]
        )
        new_pos[lower_than_bound[0]] = np.array(
            [
                np.array([u - l for u, l in zip(ub, lb)])
                * np.random.random_sample((position.shape[1],))
                + lb
            ]
        )
        return new_pos

    def intermediate(self, position, bounds, **kwargs):
        if self.memory is None:
            new_pos = position
            self.memory = new_pos
        else:
            lb, ub = bounds
            lower_than_bound, greater_than_bound = self._out_of_bounds(
                position, bounds
            )
            new_pos = position
            new_pos[lower_than_bound] = 0.5 * (
                    self.memory[lower_than_bound] + lb[lower_than_bound[1]]
            )
            new_pos[greater_than_bound] = 0.5 * (
                    self.memory[greater_than_bound] + ub[greater_than_bound[1]]
            )
            self.memory = new_pos
        return new_pos

    def periodic(self, position, bounds, **kwargs):
        lb, ub = bounds
        lower_than_bound, greater_than_bound = self._out_of_bounds(
            position, bounds
        )
        bound_d = np.tile(
            np.abs(np.array(ub) - np.array(lb)), (position.shape[0], 1)
        )
        ub = np.tile(ub, (position.shape[0], 1))
        lb = np.tile(lb, (position.shape[0], 1))
        new_pos = position
        if lower_than_bound[0].size != 0 and lower_than_bound[1].size != 0:
            new_pos[lower_than_bound] = ub[lower_than_bound] - np.mod(
                (lb[lower_than_bound] - new_pos[lower_than_bound]),
                bound_d[lower_than_bound],
            )
        if greater_than_bound[0].size != 0 and greater_than_bound[1].size != 0:
            new_pos[greater_than_bound] = lb[greater_than_bound] + np.mod(
                (new_pos[greater_than_bound] - ub[greater_than_bound]),
                bound_d[greater_than_bound],
            )
        return new_pos


class Topology(abc.ABC):
    def __init__(self, static, **kwargs):
        """Initializes the class"""

        # Initialize logger
        self.rep = Reporter(logger=logging.getLogger(__name__))

        # Initialize attributes
        self.static = static
        self.neighbor_idx = None

        if self.static:
            self.rep.log(
                "Running on `dynamic` topology,"
                "set `static=True` for fixed neighbors.",
                lvl=logging.DEBUG,
            )

    @abc.abstractmethod
    def compute_gbest(self, swarm):
        """Compute the best particle of the swarm and return the cost and
        position"""
        raise NotImplementedError("Topology::compute_gbest()")

    @abc.abstractmethod
    def compute_position(self, swarm):
        """Update the swarm's position-matrix"""
        raise NotImplementedError("Topology::compute_position()")

    @abc.abstractmethod
    def compute_velocity(self, swarm):
        """Update the swarm's velocity-matrix"""
        raise NotImplementedError("Topology::compute_velocity()")


class Star(Topology):
    def __init__(self, static=None, **kwargs):
        # static = None is just an artifact to make the API consistent
        # Setting it will not change swarm behavior
        super(Star, self).__init__(static=True)
        self.rep = Reporter(logger=logging.getLogger(__name__))

    def compute_gbest(self, swarm, **kwargs):
        try:
            if self.neighbor_idx is None:
                self.neighbor_idx = np.tile(
                    np.arange(swarm.n_particles), (swarm.n_particles, 1)
                )
            if np.min(swarm.pbest_cost) < swarm.best_cost:
                # Get the particle position with the lowest pbest_cost
                # and assign it to be the best_pos
                best_pos = swarm.pbest_pos[np.argmin(swarm.pbest_cost)]
                best_cost = np.min(swarm.pbest_cost)
            else:
                # Just get the previous best_pos and best_cost
                best_pos, best_cost = swarm.best_pos, swarm.best_cost
        except AttributeError:
            self.rep.logger.exception(
                "Please pass a Swarm class. You passed {}".format(type(swarm))
            )
            raise
        else:
            return (best_pos, best_cost)

    def compute_velocity(self, swarm, clamp=None, vh=VelocityHandler(strategy="unmodified"), bounds=None, ):
        return compute_velocity(swarm, clamp, vh, bounds=bounds)

    def compute_position(self, swarm, bounds=None, bh=BoundaryHandler(strategy="periodic")):
        return compute_position(swarm, bounds, bh)


@attrs
class Swarm(object):
    # Required attributes
    position = attrib(type=np.ndarray, validator=instance_of(np.ndarray))
    velocity = attrib(type=np.ndarray, validator=instance_of(np.ndarray))
    # With defaults
    n_particles = attrib(type=int, validator=instance_of(int))
    dimensions = attrib(type=int, validator=instance_of(int))
    options = attrib(type=dict, default={}, validator=instance_of(dict))
    pbest_pos = attrib(type=np.ndarray, validator=instance_of(np.ndarray))
    best_pos = attrib(
        type=np.ndarray,
        default=np.array([]),
        validator=instance_of(np.ndarray),
    )
    pbest_cost = attrib(
        type=np.ndarray,
        default=np.array([]),
        validator=instance_of(np.ndarray),
    )
    best_cost = attrib(
        type=float, default=np.inf, validator=instance_of((int, float))
    )
    current_cost = attrib(
        type=np.ndarray,
        default=np.array([]),
        validator=instance_of(np.ndarray),
    )

    @n_particles.default
    def n_particles_default(self):
        return self.position.shape[0]

    @dimensions.default
    def dimensions_default(self):
        return self.position.shape[1]

    @pbest_pos.default
    def pbest_pos_default(self):
        return self.position


class SwarmOptimizer(abc.ABC):
    def __init__(self, n_particles, dimensions, options, bounds=None, velocity_clamp=None, center=1.0, ftol=-np.inf,
                 ftol_iter=1, init_pos=None, ):

        # Initialize primary swarm attributes
        self.n_particles = n_particles
        self.dimensions = dimensions
        self.bounds = bounds
        self.velocity_clamp = velocity_clamp
        self.swarm_size = (n_particles, dimensions)
        self.options = options
        self.center = center
        self.ftol = ftol

        try:
            assert ftol_iter > 0 and isinstance(ftol_iter, int)
        except AssertionError:
            raise AssertionError(
                "ftol_iter expects an integer value greater than 0"
            )

        self.ftol_iter = ftol_iter
        self.init_pos = init_pos
        # Initialize named tuple for populating the history list
        self.ToHistory = namedtuple(
            "ToHistory",
            [
                "best_cost",
                "mean_pbest_cost",
                "mean_neighbor_cost",
                "position",
                "velocity",
            ],
        )
        # Initialize resettable attributes
        self.reset()

    def _populate_history(self, hist):
        self.cost_history.append(hist.best_cost)
        self.mean_pbest_history.append(hist.mean_pbest_cost)
        self.mean_neighbor_history.append(hist.mean_neighbor_cost)
        self.pos_history.append(hist.position)
        self.velocity_history.append(hist.velocity)

    @abc.abstractmethod
    def optimize(self, objective_func, iters, n_processes=None, **kwargs):
        raise NotImplementedError("SwarmOptimizer::optimize()")

    def reset(self):
        # Initialize history lists
        self.cost_history = []
        self.mean_pbest_history = []
        self.mean_neighbor_history = []
        self.pos_history = []
        self.velocity_history = []

        # Initialize the swarm
        self.swarm = create_swarm(
            n_particles=self.n_particles,
            dimensions=self.dimensions,
            bounds=self.bounds,
            center=self.center,
            init_pos=self.init_pos,
            clamp=self.velocity_clamp,
            options=self.options,
        )


class PSO(SwarmOptimizer):
    def __init__(self, n_particles, dimensions, options, bounds=None, oh_strategy=None, bh_strategy="periodic",
                 velocity_clamp=None, vh_strategy="unmodified", center=1.00, ftol=-np.inf, ftol_iter=1,
                 init_pos=None, ):
        super(PSO, self).__init__(n_particles=n_particles, dimensions=dimensions, options=options, bounds=bounds,
                                  velocity_clamp=velocity_clamp, center=center, ftol=ftol, ftol_iter=ftol_iter,
                                  init_pos=init_pos, )

        if oh_strategy is None:
            oh_strategy = {}
        # Initialize logger
        self.rep = Reporter(logger=logging.getLogger(__name__))
        # Initialize the resettable attributes
        self.reset()
        # Initialize the topology
        self.top = Star()
        self.bh = BoundaryHandler(strategy=bh_strategy)
        self.vh = VelocityHandler(strategy=vh_strategy)
        self.oh = OptionsHandler(strategy=oh_strategy)
        self.name = __name__

    def optimize(self, objective_func, iters, verbose=True, **kwargs):
        # Apply verbosity
        if verbose:
            log_level = logging.INFO
        else:
            log_level = logging.NOTSET

        # Populate memory of the handlers
        self.bh.memory = self.swarm.position
        self.vh.memory = self.swarm.position

        self.swarm.pbest_cost = np.full(self.swarm_size[0], np.inf)
        ftol_history = deque(maxlen=self.ftol_iter)
        for i in self.rep.pbar(iters, self.name) if verbose else range(iters):
            # Compute cost for current position and personal best
            self.swarm.current_cost = compute_objective_function(self.swarm, objective_func, **kwargs)
            # print(self.swarm.current_cost)
            self.swarm.pbest_pos, self.swarm.pbest_cost = compute_pbest(self.swarm)
            # Set best_cost_yet_found for ftol
            best_cost_yet_found = self.swarm.best_cost
            self.swarm.best_pos, self.swarm.best_cost = self.top.compute_gbest(self.swarm)

            if verbose:
                self.rep.hook(best_cost=self.swarm.best_cost)

            # Save to history
            hist = self.ToHistory(
                best_cost=self.swarm.best_cost,
                mean_pbest_cost=np.mean(self.swarm.pbest_cost),
                mean_neighbor_cost=self.swarm.best_cost,
                position=self.swarm.position,
                velocity=self.swarm.velocity,
            )

            self._populate_history(hist)

            # Verify stop criteria based on the relative acceptable cost ftol
            relative_measure = self.ftol * (1 + np.abs(best_cost_yet_found))
            delta = (
                    np.abs(self.swarm.best_cost - best_cost_yet_found)
                    < relative_measure
            )
            if i < self.ftol_iter:
                ftol_history.append(delta)
            else:
                ftol_history.append(delta)
                if all(ftol_history):
                    break
            # Perform options update
            self.swarm.options = self.oh(
                self.options, iternow=i, itermax=iters
            )
            # Perform velocity and position updates
            self.swarm.velocity = self.top.compute_velocity(
                self.swarm, self.velocity_clamp, self.vh, self.bounds
            )
            self.swarm.position = self.top.compute_position(
                self.swarm, self.bounds, self.bh
            )
        # Obtain the final best_cost and the final best_position
        final_best_cost = self.swarm.best_cost.copy()
        final_best_pos = self.swarm.pbest_pos[
            self.swarm.pbest_cost.argmin()
        ].copy()

        # Write report in log and return final cost and position
        self.rep.log("Optimization finished with best cost: {}".format(final_best_cost), lvl=log_level, )

        return final_best_cost, final_best_pos


class OptionsHandler(HandlerMixin):
    def __init__(self, strategy):
        self.strategy = strategy
        self.strategies = self._get_all_strategies()
        self.rep = Reporter(logger=logging.getLogger(__name__))

    def __call__(self, start_opts, **kwargs):
        try:
            if not self.strategy:
                return start_opts
            return_opts = copy(start_opts)
            for opt in start_opts:
                if opt in self.strategy:
                    return_opts[opt] = self.strategies[self.strategy[opt]](
                        start_opts, opt, **kwargs
                    )
        except KeyError:
            message = "Unrecognized strategy: {}. Choose one among: " + str(
                [strat for strat in self.strategies.keys()]
            )
            self.rep.logger.exception(message.format(self.strategy))
            raise
        else:
            return return_opts

    def exp_decay(self, start_opts, opt, **kwargs):
        try:
            # default values from reference paper
            if "d1" not in kwargs:
                d1 = 0.2
            else:
                d1 = kwargs["d1"]
            if "d2" not in kwargs:
                d2 = 7
            else:
                d2 = kwargs["d2"]

            end_opts = {
                "w": 0.4,
                "c1": 0.8 * start_opts["c1"],
                "c2": 1 * start_opts["c2"],
            }
            if "end_opts" in kwargs:
                if opt in kwargs["end_opts"]:
                    end_opts[opt] = kwargs["end_opts"][opt]
            start = start_opts[opt]
            end = end_opts[opt]
            new_val = (start - end - d1) * math.exp(
                1 / (1 + d2 * kwargs["iternow"] / kwargs["itermax"])
            )
        except KeyError:
            self.rep.logger.exception("Keyword 'itermax' or 'iternow' missing")
            raise
        else:
            return new_val

    def lin_variation(self, start_opts, opt, **kwargs):
        try:
            end_opts = {
                "w": 0.4,
                "c1": 0.8 * start_opts["c1"],
                "c2": 1 * start_opts["c2"],
            }
            if "end_opts" in kwargs:
                if opt in kwargs["end_opts"]:
                    end_opts[opt] = kwargs["end_opts"][opt]
            start = start_opts[opt]
            end = end_opts[opt]
            new_val = (
                    end
                    + (start - end)
                    * (kwargs["itermax"] - kwargs["iternow"])
                    / kwargs["itermax"]
            )
        except KeyError:
            self.rep.logger.exception("Keyword 'itermax' or 'iternow' missing")
            raise
        else:
            return new_val

    def random(self, start_opts, opt, **kwargs):
        start = start_opts[opt]
        if opt in kwargs["end_opts"]:
            end = kwargs["end_opts"][opt]
        else:
            end = start + 1
        return start + (end - start) * np.random.rand()

    def nonlin_mod(self, start_opts, opt, **kwargs):
        try:
            if "n" not in kwargs:
                n = 1.2
            else:
                n = kwargs["n"]

            end_opts = {
                "w": 0.4,
                "c1": 0.8 * start_opts["c1"],
                "c2": 1 * start_opts["c2"],
            }
            if "end_opts" in kwargs:
                if opt in kwargs["end_opts"]:
                    end_opts[opt] = kwargs["end_opts"][opt]

            start = start_opts[opt]
            end = end_opts[opt]
            new_val = end + (start - end) * (
                    (kwargs["itermax"] - kwargs["iternow"]) ** n
                    / kwargs["itermax"] ** n
            )
        except KeyError:
            self.rep.logger.exception("Keyword 'itermax' or 'iternow' missing")
            raise
        else:
            return new_val


def generate_velocity(n_particles, dimensions, clamp=None):
    try:
        min_velocity, max_velocity = (0, 1) if clamp is None else clamp
        velocity = (max_velocity - min_velocity) * np.random.random_sample(
            size=(n_particles, dimensions)
        ) + min_velocity
    except ValueError:
        msg = "Please check clamp shape: {} != {}"
        rep.logger.exception(msg.format(len(clamp), dimensions))
        raise
    except TypeError:
        msg = "generate_velocity() takes an int for n_particles and dimensions and an array for clamp"
        rep.logger.exception(msg)
        raise
    else:
        return velocity


def generate_swarm(n_particles, dimensions, bounds=None, center=1.00, init_pos=None):
    try:
        if (init_pos is not None) and (bounds is None):
            pos = np.tile(init_pos, (n_particles, 1))
            # print(pos)
        elif (init_pos is not None) and (bounds is not None):
            if not (np.all(bounds[0] <= init_pos) and np.all(init_pos <= bounds[1])):
                raise ValueError("User-defined init_pos is out of bounds.")
            pos = init_pos
        elif (init_pos is None) and (bounds is None):
            pos = center * np.random.uniform(
                low=0.0, high=1.0, size=(n_particles, dimensions)
            )
        else:
            lb, ub = bounds
            min_bounds = np.repeat(
                np.array(lb)[np.newaxis, :], n_particles, axis=0
            )
            max_bounds = np.repeat(
                np.array(ub)[np.newaxis, :], n_particles, axis=0
            )
            pos = center * np.random.uniform(
                low=min_bounds, high=max_bounds, size=(n_particles, dimensions)
            )
    except ValueError:
        msg = "Bounds and/or init_pos should be of size ({},)"
        rep.logger.exception(msg.format(dimensions))
        raise
    except TypeError:
        msg = "generate_swarm() takes an int for n_particles and dimensions and an array for bounds"
        rep.logger.exception(msg)
        raise
    else:
        return pos


def create_swarm(n_particles, dimensions, options={}, bounds=None, center=1.0, init_pos=None, clamp=None, ):
    position = generate_swarm(
        n_particles,
        dimensions,
        bounds=bounds,
        center=center,
        init_pos=init_pos,
    )

    velocity = generate_velocity(n_particles, dimensions, clamp=clamp)
    return Swarm(position, velocity, options=options)


def compute_position(swarm, bounds, bh):
    try:
        temp_position = swarm.position.copy()
        temp_position += swarm.velocity

        if bounds is not None:
            temp_position = bh(temp_position, bounds)

        position = temp_position
    except AttributeError:
        rep.logger.exception(
            "Please pass a Swarm class. You passed {}".format(type(swarm))
        )
        raise
    else:
        return position


def compute_velocity(swarm, clamp, vh, bounds=None):
    try:
        # Prepare parameters
        swarm_size = swarm.position.shape
        c1 = swarm.options["c1"]
        c2 = swarm.options["c2"]
        w = swarm.options["w"]
        # Compute for cognitive and social terms
        cognitive = (
                c1
                * np.random.uniform(0, 1, swarm_size)
                * (swarm.pbest_pos - swarm.position)
        )
        social = (
                c2
                * np.random.uniform(0, 1, swarm_size)
                * (swarm.best_pos - swarm.position)
        )
        # Compute temp velocity (subject to clamping if possible)
        temp_velocity = (w * swarm.velocity) + cognitive + social
        updated_velocity = vh(
            temp_velocity, clamp, position=swarm.position, bounds=bounds
        )

    except AttributeError:
        rep.logger.exception(
            "Please pass a Swarm class. You passed {}".format(type(swarm))
        )
        raise
    except KeyError:
        rep.logger.exception("Missing keyword in swarm.options")
        raise
    else:
        return updated_velocity


def compute_pbest(swarm):
    try:
        # Infer dimensions from positions
        dimensions = swarm.dimensions
        # Create a 1-D and 2-D mask based from comparisons
        mask_cost = swarm.current_cost < swarm.pbest_cost
        mask_pos = np.repeat(mask_cost[:, np.newaxis], dimensions, axis=1)
        # Apply masks
        new_pbest_pos = np.where(~mask_pos, swarm.pbest_pos, swarm.position)
        new_pbest_cost = np.where(
            ~mask_cost, swarm.pbest_cost, swarm.current_cost
        )
    except AttributeError:
        rep.logger.exception(
            "Please pass a Swarm class. You passed {}".format(type(swarm))
        )
        raise
    else:
        return (new_pbest_pos, new_pbest_cost)


def compute_objective_function(swarm, objective_func, **kwargs):
    return objective_func(swarm.position, **kwargs)


# Calculated from the training data
def get_var_of_distance(dis):
    if model == "uwb":
        # print(dis, math.fabs(0.0000157158329215012*(dis**2.15455644620686)))
        return math.fabs(0.0000157158329215012 * (dis ** 2.15455644620686))
    elif model == "ble":
        return math.fabs(0.003 * dis * 100 - 0.2378)


def get_distance(input_nodeA_pos, input_nodeB_pos):
    distances = (input_nodeA_pos - input_nodeB_pos) ** 2
    distances = distances.sum(axis=-1)
    distances = np.sqrt(distances)

    return distances


def get_tensor_idx_from_node_id(input_node_id):
    # rep.log("Getting tensor idx for nodeID: " + str(input_node_id))
    # rep.log(tensor_idx_from_node_id)
    return tensor_idx_from_node_id[input_node_id]


def get_node_id_from_tensor_idx(input_node_tensor_id):
    return node_ids[input_node_tensor_id]


# Extract from current tensor, the positions for requested node
def get_nodepair_pos_from_matrix(full_parameters, tensor_id_A, tensor_id_B):
    global particles

    pos_tensor_id_A = []
    pos_tensor_id_B = []

    for particle in range(particles):
        pos_tensor_id_A.append(
            (full_parameters[tensor_id_A * 2][particle], full_parameters[tensor_id_A * 2 + 1][particle]))

        pos_tensor_id_B.append(
            (full_parameters[tensor_id_B * 2][particle], full_parameters[tensor_id_B * 2 + 1][particle]))

    # rep.log("full_parameters")
    # rep.log(full_parameters)

    return np.array(pos_tensor_id_A), np.array(pos_tensor_id_B)


def get_true_positions():
    global true_positions, node_ids

    temp_true_pos = []

    for node_id in node_ids:
        temp_true_pos.append(true_positions[node_id][0])
        temp_true_pos.append(true_positions[node_id][1])

    return temp_true_pos


def get_ml_pso_solution(input_estimated_node_positions):
    global node_ids, neighborhoods, measured_distances_stats, number_of_nodes, global_counter

    # rep.log("input_estimated_Node_positions")
    # rep.log(len(input_estimated_Node_positions))
    # rep.log(input_estimated_Node_positions)
    # rep.log(input_estimated_Node_positions[0])

    # if global_counter == 0:
    #     # rep.log("Global Counter: " + str(global_counter))
    #     # Use this part to override one table for debugging
    #     # Regardless, the 0th evaluation is never considered for estimating and comparing the CL's performance.
    #     input_estimated_node_positions[0] = get_true_positions()
    #     global_counter = global_counter + 1

    # rep.log("input_estimated_Node_positions")
    # rep.log(len(input_estimated_node_positions))
    # rep.log(input_estimated_node_positions)
    # rep.log(input_estimated_node_positions[0])

    full_parameters = [input_estimated_node_positions[:, i] for i in
                       range(all_coord_variables)]  # all_coord_variables := all x,y coordinates

    # print("full_parameters",flush=True)
    # print(full_parameters, flush=True)
    # exit()

    score = 0

    # Remember that the ID's start from 0. We have another data structure mapping to the real ID's
    for node_tensor_id in range(number_of_nodes):

        # Get the original nodeID that corresponds to current node_tensor_id
        node_id = get_node_id_from_tensor_idx(node_tensor_id)
        # rep.log("Node: " + str(node_id))
        # rep.log("Total neighborhoods[node_id] (" + str(len(neighborhoods[node_id])) + "):")
        # rep.log(neighborhoods[node_id])

        for neighbor_tensor_id in [get_tensor_idx_from_node_id(neighbor) for neighbor in neighborhoods[node_id]]:
            # rep.log("len(input_estimated_Node_positions)")
            # rep.log(len(input_estimated_Node_positions))

            # rep.log("len(input_estimated_Node_positions[0])")
            # rep.log(len(input_estimated_Node_positions[0]))

            # rep.log("input_estimated_Node_positions[0]")
            # rep.log(input_estimated_Node_positions[0])

            nodeA_pos, nodeB_pos = get_nodepair_pos_from_matrix(full_parameters, node_tensor_id, neighbor_tensor_id)

            # rep.log("NodeA_ID: " + str(node_tensor_id) + " NodeA Pos: " + str(nodeA_pos) + ", NodeB_ID: " + str(neighbor_tensor_id) + " NodeB Pos: " + str(nodeB_pos))

            evaluated_distance_between_ij = get_distance(nodeA_pos, nodeB_pos)

            # rep.log("evaluated_distance_between_ij")
            # rep.log(evaluated_distance_between_ij)

            neighbor_id = get_node_id_from_tensor_idx(neighbor_tensor_id)
            var_of_distance_measurement = measured_distances_stats[node_id][neighbor_id][1]
            score = score + (1 / var_of_distance_measurement) * (measured_distances_stats[node_id][neighbor_id][0] - evaluated_distance_between_ij) ** 2
            # score = score + (measured_distances_stats[node_id][neighbor_id][0] - estimated_distance_between_nodes) ** 2

            # rep.log("node_id: " + str(node_id) + " node_tensor_id: " + str(node_tensor_id) + " neighbor_id: " + str(neighbor_id) + " neighbor_tensor_id: " + str(neighbor_tensor_id) + " score: " + str(score))

    # rep.log("score")
    # rep.log(score)

    return score


def get_reference_node_id_from_log_entry(parsed_line):
    flag = "Node: "

    start_index = parsed_line.find(flag)
    end_index = parsed_line.find(", ", start_index, -1)

    return int(parsed_line[start_index + len(flag): end_index])


def get_initial_random_node_pos_from_log_entry(parsed_line):
    x_start_flag = "["
    x_start_index = find_nth(parsed_line, x_start_flag, 1)
    x_end_flag = ", "
    x_end_index = find_nth(parsed_line, x_end_flag, 3)
    x_pos = float(parsed_line[x_start_index + len(x_start_flag): x_end_index].replace(",", "."))

    y_start_flag = ", "
    y_start_index = find_nth(parsed_line, y_start_flag, 3)
    y_end_flag = "]"
    y_end_index = find_nth(parsed_line, y_end_flag, 1)
    y_pos = float(parsed_line[y_start_index + len(y_start_flag): y_end_index].replace(",", "."))

    return [x_pos, y_pos]


def get_true_node_pos_from_log_entry(parsed_line):
    x_start_flag = "["
    x_start_index = find_nth(parsed_line, x_start_flag, 0)
    x_end_flag = ", "
    x_end_index = find_nth(parsed_line, x_end_flag, 1)
    x_pos = float(parsed_line[x_start_index + len(x_start_flag): x_end_index])

    y_start_flag = ", "
    y_start_index = find_nth(parsed_line, y_start_flag, 1)
    y_end_flag = "]"
    y_end_index = find_nth(parsed_line, y_end_flag, 0)
    y_pos = float(parsed_line[y_start_index + len(y_start_flag): y_end_index])

    if model == "uwb":
        x_pos = x_pos * 100
        y_pos = y_pos * 100

    return [x_pos, y_pos]


def find_nth(given_string, part, n):
    parts = given_string.split(part, n + 1)
    if len(parts) <= n + 1:
        return -1
    return len(given_string) - len(parts[-1]) - len(part)


def unzip_arlcl_results():
    # Check if we have already extracted the ARLCL results
    if not file_exists(ARLCL_temp_export_scenario_path):
        rep.log("Extracting the ARLCL data at " + ARLCL_temp_export_scenario_path)
        with zipfile.ZipFile(ARLCL_zipped_scenario_path, 'r') as zipObj:
            for sub_file in zipObj.namelist():
                if sub_file.endswith(".log"):
                    zipObj.extract(sub_file, path=ARLCL_temp_export_path, pwd=None)


def get_db_positions(eval_iter):
    temp_initial_node_positions = {}
    temp_initial_node_positions_array = []
    temp_true_node_positions = {}

    arlcl_exported_eval_scenario_results_path = ARLCL_temp_export_scenario_path + "/" + str(eval_iter) + "/results.log"

    rep.log("Opening " + arlcl_exported_eval_scenario_results_path + " to get the init data")

    with open(arlcl_exported_eval_scenario_results_path, 'r') as input_DB_file:
        for line in input_DB_file:

            # print(line, flush=True)

            if line[0:4] == "Node":
                # rep.log(line.replace("\n", ""))
                # Get the reference Node ID
                node_id = get_reference_node_id_from_log_entry(line)

                # Get the initial random Node pos
                init_position = get_initial_random_node_pos_from_log_entry(line)
                true_position = get_true_node_pos_from_log_entry(line)

                temp_initial_node_positions[node_id] = init_position
                temp_initial_node_positions_array.append(init_position[0])
                temp_initial_node_positions_array.append(init_position[1])

                temp_true_node_positions[node_id] = true_position

                # rep.log("Node: " + str(node_id) + ", Pos: " + str(current_pos))

    return np.array(temp_initial_node_positions_array), temp_initial_node_positions, temp_true_node_positions


def get_evaluation_measurements(eval_iter):
    global measurement, model, scenario_DB_path

    temp_neighbors = {}
    temp_measured_distances = {}

    rep.log("Opening " + scenario_DB_path + " to get the evaluation measurements")

    with open(scenario_DB_path, 'r') as input_data:

        new_section_for_averaged_resampling = False

        for line in input_data:

            line = line.replace("\n", "")
            if line == "#" + measurement[model]["unit"] + "_" + str(eval_iter) + "#":
                new_section_for_averaged_resampling = True
                continue
            if new_section_for_averaged_resampling:
                # Identify the end of current iter section to exit
                # (to avoid parsing sampled measurements corresponding to other evaluation repetitions)
                if line == "":
                    return temp_neighbors, temp_measured_distances

                line_parts = line.split(":")

                nodeA, nodeB = [int(parsed_id) for parsed_id in line_parts[0].split(";")]

                if nodeA in temp_neighbors:
                    temp_neighbors[nodeA].append(nodeB)
                else:
                    temp_neighbors[nodeA] = [nodeB]
                    temp_measured_distances[nodeA] = {}

                current_measured_distance = float(line_parts[1].split("&")[1].replace(",", "."))

                # print(current_distance, flush=True)

                temp_measured_distances[nodeA][nodeB] = (
                current_measured_distance, get_var_of_distance(current_measured_distance))

        # Being here means that we have finished iterating the file
        return temp_neighbors, temp_measured_distances


def store_positioning_results(exported_results_filename, resulted_positions):
    global node_ids

    # rep.log(vertex_positions)
    # rep.log(index_mapping)

    with open(exported_results_filename, "w") as out_data:
        for nodeID in range(len(node_ids)):
            line_to_write = "Node:" + str(node_ids[nodeID]) + ";" + str(
                round(resulted_positions[0][nodeID], 3)) + ":" + str(round(resulted_positions[1][nodeID], 3)) + "\n"
            out_data.write(line_to_write)


def get_points_from_results(pos_results):
    temp_x = []
    temp_y = []

    iter = 1

    # rep.log(pos_results)

    for pos in pos_results:
        # Check if number is odd
        if iter % 2 == 1:
            temp_x.append(pos)
        else:
            temp_y.append(pos)

        iter = iter + 1

    return temp_x, temp_y


# Define the objective function to be minimized
def objective_function(x):

    global number_of_nodes, particles, measured_distances_stats, neighborhoods

    # print(x, flush=True)
    # print(len(x[0]), flush=True)

    errors = np.zeros(particles)
    # print("errors", flush=True)
    # print(errors, flush=True)

    # We have a new guess about the nodes' positions and we need to get the cross distances under this specific setting
    # Remember that the ID's start from 0. We have another data structure mapping to the real ID's
    for node_tensor_id in range(number_of_nodes):

        # Get the original nodeID that corresponds to current node_tensor_id
        node_id = get_node_id_from_tensor_idx(node_tensor_id)
        # rep.log("Node: " + str(node_id))
        # rep.log("Total neighborhoods[node_id] (" + str(len(neighborhoods[node_id])) + "):")
        # rep.log(neighborhoods[node_id])

        for neighbor_tensor_id in [get_tensor_idx_from_node_id(neighbor) for neighbor in neighborhoods[node_id]]:

            # Get the original nodeID that corresponds to current neighbor_tensor_id
            neighbor_id = get_node_id_from_tensor_idx(neighbor_tensor_id)

            var_of_distance_measurement = measured_distances_stats[node_id][neighbor_id][1]

            # rep.log("len(input_estimated_Node_positions)")
            # rep.log(len(input_estimated_Node_positions))

            # rep.log("len(input_estimated_Node_positions[0])")
            # rep.log(len(input_estimated_Node_positions[0]))

            # rep.log("input_estimated_Node_positions[0]")
            # rep.log(input_estimated_Node_positions[0])

            # The distance between i,j across all particles (sets) in an array
            evaluated_distance_between_ij = np.sqrt(
                (x[:, node_tensor_id*2] - x[:, neighbor_tensor_id*2])**2 +
                (x[:, node_tensor_id*2+1] - x[:, neighbor_tensor_id*2+1])**2
            )
            # rep.log("evaluated_distance_between_ij")
            # rep.log(evaluated_distance_between_ij)

            # print(node_tensor_id, neighbor_tensor_id, evaluated_distance_between_ij, flush=True)

            errors += (1 / var_of_distance_measurement) * (evaluated_distance_between_ij - measured_distances_stats[node_id][neighbor_id][0]) ** 2

    return errors


def calculate_swarm_positions(eval_iter, scenario_eval_results_path):
    global init_positions, true_positions, measured_distances_stats, neighborhoods, options, particles, optimization_iterations
    # Node positions
    initial_node_positions_array, init_positions, true_positions = get_db_positions(eval_iter)

    rep.log("init_positions")
    rep.log(init_positions)

    rep.log("true_positions")
    rep.log(true_positions)

    neighborhoods, measured_distances_stats = get_evaluation_measurements(eval_iter)

    # rep.log("neighborhoods")
    # rep.log(neighborhoods)

    # rep.log("estimated_distances")
    # rep.log(estimated_distances)

    optimizer = PSO(n_particles=particles, dimensions=all_coord_variables, options=options, init_pos=initial_node_positions_array)

    # Perform optimization
    cost, pos = optimizer.optimize(get_ml_pso_solution, iters=optimization_iterations, verbose=verbose_logging)

    # rep.log("Solution (cost, pos)")
    # rep.log(cost)

    # For plotting the results
    x, y = get_points_from_results(pos)

    store_positioning_results(scenario_eval_results_path, (x, y))


def file_exists(filepath):
    if os.path.exists(filepath):
        return True
    return False


def eval_id_already_available(export, eval_scenario):
    filepath = export + eval_scenario
    if os.path.isfile(filepath):
        rep.log("Folder exists")
        return True
    return False


def delete_folder(path):
    rep.log("Deleting possible leftovers at " + path)
    try:
        shutil.rmtree(path)
    except OSError as e:
        rep.log("Notice: %s - %s." % (e.filename, e.strerror))


def run():
    global true_positions, node_pairs, init_positions, measured_distances_stats, neighborhoods

    rep.log("\nCheck whether the final ML_PSO results for current scenario (" + scenario + ") already exist.")
    # Check first whether the final ML_PSO results for this scenario (a zip file) already exist
    if file_exists(ML_PSO_zip_scenario_path):
        rep.log("Current scenario (" + scenario + ") has already been processed successfully.")

        # Clean any probable old temporal ARLCL or ML_PSO data, related to current scenario
        delete_folder(ARLCL_temp_export_scenario_path)
        delete_folder(ML_PSO_export_scenario_path)

        # Since the file already exists, we can terminate the script here
        exit()
    else:
        rep.log("The final ML_PSO results for current scenario (" + scenario + ") do not exist.")
        # Clean any probable old temporal ARLCL data, related to current scenario
        delete_folder(ARLCL_temp_export_scenario_path)

        # Since the final ML_PSO results for current scenario is not yet available,
        # unzip in the temp folder, the corresponding ARLCL results for this scenario
        unzip_arlcl_results()

    # At this point we are sure that we have unzipped the ARLCL results related to this scenario
    # Also, although we did not find the final ML_PSO results for this scenario,
    # check whether there are any older preprocessed data
    if not file_exists(ML_PSO_export_scenario_path):
        rep.log("Scenario " + scenario + " has not been initiated before. Creating folder.")
        # if not, then we can create the folder and continue
        os.mkdir(ML_PSO_export_scenario_path)
    else:
        rep.log("Scenario " + scenario + " has been initiated before but is not complete. Continuing the evaluations.")

    # Remember that eval=0 is perfect
    for eval_iter in range(last_evaluation_id + 1):
        true_positions = {}
        node_pairs = None
        init_positions = None
        measured_distances_stats = None
        neighborhoods = None

        rep.log("\nStart optimizing evaluation scenario of ID: " + str(eval_iter))

        ML_PSO_exported_eval_scenario_path = ML_PSO_export_scenario_path + "/" + str(eval_iter)
        ML_PSO_exported_eval_scenario_results_path = ML_PSO_exported_eval_scenario_path + "/results.log"
        rep.log("Searching for previous optimization results at: " + ML_PSO_exported_eval_scenario_results_path)

        # Check first whether current evaluation iteration has results ready
        if file_exists(ML_PSO_exported_eval_scenario_results_path):
            rep.log("Previous results for eval scenario " + scenario + " found")
            rep.log("Skipping current evaluation iteration")
            pass
        else:
            rep.log("No previous results found")
            # Clean and recreate the target ML_PSO results folder, related to current scenario
            delete_folder(ML_PSO_exported_eval_scenario_path)
            os.mkdir(ML_PSO_exported_eval_scenario_path)

            calculate_swarm_positions(eval_iter, ML_PSO_exported_eval_scenario_results_path)

        # exit()  # Used to constraint only to 1 iteration


def zip_ML_PSO_results():
    global ML_PSO_zip_scenario_path, ML_PSO_export_scenario_path

    rep.log("Storing the ML_PSO Results data at " + ML_PSO_zip_scenario_path)

    zipf = zipfile.ZipFile(ML_PSO_zip_scenario_path, 'w', zipfile.ZIP_DEFLATED)

    for root, dirs, files in os.walk(ML_PSO_export_scenario_path):
        for file in files:
            zipf.write(os.path.join(root, file),
                       os.path.relpath(os.path.join(root, file),
                                       os.path.join(ML_PSO_export_scenario_path, '..')))
    zipf.close()


def set_paths():
    global DB_path, scenario, scenario_DB_path, ML_PSO_export_scenario_path, ML_PSO_zip_scenario_path, \
        ARLCL_zipped_scenario_path, ARLCL_temp_export_path, ARLCL_temp_export_scenario_path, measurement, model, \
        zipped_arlcl_results_path, ML_PSO_export_path

    parent_arlcl_results_dir = os.path.dirname(zipped_arlcl_results_path)

    scenario_DB_path = os.path.join(DB_path, scenario + measurement[model]["db_ext"])

    if not os.path.exists(ML_PSO_export_path):
        os.makedirs(ML_PSO_export_path)
        os.chmod(ML_PSO_export_path, 0o777)

    ML_PSO_export_scenario_path = os.path.join(ML_PSO_export_path, scenario)
    ML_PSO_zip_scenario_path = ML_PSO_export_scenario_path + ".zip"
    ARLCL_zipped_scenario_path = os.path.join(zipped_arlcl_results_path, scenario + ".zip")
    ARLCL_temp_export_path = os.path.join(parent_arlcl_results_dir, model.upper() + "_Estimations_temp_unzipped")

    if not os.path.exists(ARLCL_temp_export_path):
        os.makedirs(ARLCL_temp_export_path)
        os.chmod(ARLCL_temp_export_path, 0o777)

    ARLCL_temp_export_scenario_path = os.path.join(ARLCL_temp_export_path, scenario)

    rep.log("\nSetting other paths: ")
    rep.log("Scenario DB path: " + scenario_DB_path)
    rep.log("ML-PSO export path: " + ML_PSO_export_path)
    rep.log("ML-PSO export scenario path: " + ML_PSO_export_scenario_path)
    rep.log("ML-PSO zip scenario path: " + ML_PSO_zip_scenario_path)
    rep.log("ARLCL zipped scenario path: " + ARLCL_zipped_scenario_path)
    rep.log("ARLCL export path: " + ARLCL_temp_export_path)


try:
    rep.log("Starting")
    rep.log("\nInput params: ")
    rep.log(str_params)

    global_counter = 0

    node_ids = [int(parsed_id) for parsed_id in scenario.split("_")[1].split(",")]
    # rep.log("node_ids")
    # rep.log(node_ids)

    iter_counter = 0
    tensor_idx_from_node_id = {}

    # build the index mapping
    for true_nodeID in node_ids:
        tensor_idx_from_node_id[true_nodeID] = iter_counter
        iter_counter = iter_counter + 1

    # Set-up opt searching parameters
    options = {'c1': c1_arg, 'c2': c2_arg, 'w': w_arg}

    true_positions = None
    number_of_nodes = len(node_ids)
    all_coord_variables = number_of_nodes * 2

    # Set the required paths first
    set_paths()

    # Execute the optimization
    run()

    # Store the results and clean the temp folder
    zip_ML_PSO_results()

    print(ML_PSO_export_scenario_path, flush=True)
    print(ARLCL_temp_export_scenario_path, flush=True)

    delete_folder(ML_PSO_export_scenario_path)
    # Clean any probable old temporal ARLCL data, related to current scenario
    delete_folder(ARLCL_temp_export_scenario_path)

except Exception as e:
    print("Termination %s" % e)
