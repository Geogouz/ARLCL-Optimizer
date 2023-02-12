rem #######################################################################################################
rem ============== Example for executing a BLE-based localization scenario in headless mode ===============
rem #######################################################################################################
venv/Scripts/python.exe ML-PSO_Localization.py
log_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/BLE/Export/BLE-log.txt"
zarp="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/BLE/Export/ARLCL_BLE_Estimations"
db_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/BLE/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Scenarios"
scenarios_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/BLE/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Listed Scenarios.txt"
model="BLE"
scenario_idx=0
end_eval=1
c1=0.7
c2=0.3
w=0.9
opts=500
particles=500

exit

rem #######################################################################################################
rem ============== Example for executing an UWB-based localization scenario in headless mode ==============
rem #######################################################################################################
venv/Scripts/python.exe ML-PSO_Localization.py
log_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/Export/UWB-log.txt"
zarp="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/Export/ARLCL_UWB_Estimations"
db_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Scenarios"
scenarios_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Listed Scenarios.txt"
model="UWB"
scenario_idx=0
end_eval=100
c1=0.7
c2=0.3
w=0.9
opts=1000
particles=700