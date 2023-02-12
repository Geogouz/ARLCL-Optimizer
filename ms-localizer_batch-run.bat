rem #######################################################################################################
rem ============== Example for executing a BLE-based localization scenario in headless mode ===============
rem #######################################################################################################
venv/Scripts/python.exe MS_Localization.py ^
log_path="Export/MS_BLE.log" ^
zarp="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/BLE/Export/ARLCL_BLE_Estimations" ^
db_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/BLE/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Scenarios" ^
scenarios_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/BLE/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Listed Scenarios.txt" ^
model="BLE" ^
scenario_idx=0 ^
end_eval=100 ^
opts=50000 ^
learn_rate=1e-2

exit

rem #######################################################################################################
rem ============== Example for executing an UWB-based localization scenario in headless mode ==============
rem #######################################################################################################
venv/Scripts/python.exe MS_Localization.py ^
log_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/Export/UWB-log.txt" ^
zarp="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/Export/ARLCL_UWB_Estimations" ^
db_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/UWB-Time_Evaluation-Samples_Engehalde-Floor1" ^
scenarios_path="D:/OneDrive - Universitaet Bern/Workspace/Swarm Positioning/UWB/various/Listed Scenarios/ips-server_25_combos.txt" ^
model="UWB" ^
scenario_idx=958 ^
end_eval=100 ^
opts=300000 ^
learn_rate=1e-2