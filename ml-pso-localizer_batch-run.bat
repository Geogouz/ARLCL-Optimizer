rem #######################################################################################################
rem ============== Example for executing a BLE-based localization scenario in headless mode ===============
rem #######################################################################################################
venv/Scripts/python.exe ML-PSO_Localization.py ^
log_path="Export/ML-PSO/BLE.log" ^
zarp="Export/ARLCL/BLE" ^
db_path="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/DB" ^
batch_path="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Batch.txt" ^
scenario_id=0 ^
model=ble ^
end_iter=100 ^
c1=0.7 ^
c2=0.3 ^
w=0.9 ^
opts=500 ^
particles=500

exit

rem #######################################################################################################
rem ============== Example for executing an UWB-based localization scenario in headless mode ==============
rem #######################################################################################################
venv/Scripts/python.exe ML-PSO_Localization.py ^
log_path="Export/ML-PSO/UWB.log" ^
zarp="Export/ARLCL/UWB" ^
db_path="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/DB" ^
batch_path="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Batch.txt" ^
scenario_id=0 ^
model=uwb
end_iter=100 ^
c1=0.7 ^
c2=0.3 ^
w=0.9 ^
opts=1000 ^
particles=700