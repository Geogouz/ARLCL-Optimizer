rem ####################################################################################################################
rem ==== Example for executing a BLE-based localization scenario in headless mode (zarp: zipped_arlcl_results_path) ====
rem ####################################################################################################################
venv/Scripts/python.exe MS_Localization.py ^
log_path="Export/MS/BLE.log" ^
zarp="Export/ARLCL/BLE" ^
db_path="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/DB" ^
batch_path="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Batch.txt" ^
scenario_id=0 ^
model=ble ^
end_iter=100 ^
opts=50000 ^
learn_rate=1e-2

exit

rem ####################################################################################################################
rem ==== Example for executing an UWB-based localization scenario in headless mode (zarp: zipped_arlcl_results_path) ===
rem ####################################################################################################################
venv/Scripts/python.exe MS_Localization.py ^
log_path="Export/MS/UWB.log" ^
zarp="Export/ARLCL/UWB" ^
db_path="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/DB" ^
batch_path="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Batch.txt" ^
scenario_id=0 ^
model=uwb ^
end_iter=100 ^
opts=300000 ^
learn_rate=1e-2