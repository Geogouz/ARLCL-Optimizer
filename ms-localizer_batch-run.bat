rem @echo off

set local_path=%cd%
set venv_path=%local_path%\venv\Scripts\python.exe

rem ####################################################################################################################
rem ===================== Example for executing a BLE-based localization scenario in headless mode =====================
rem ####################################################################################################################
%venv_path% "%local_path%\MS_Localization.py" ^
log="Export/MS/BLE" ^
out="Export/MS/BLE" ^
arlcl_out="Export/ARLCL/BLE" ^
db="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/DB" ^
batch="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Batch.txt" ^
scenario_id=0 ^
model=ble ^
seed=0 ^
end_iter=100 ^
opts=100000 ^
learn_rate=1e-3

rem pause
exit

rem          or.....
rem ####################################################################################################################
rem ===================== Example for executing an UWB-based localization scenario in headless mode ====================
rem ####################################################################################################################
%venv_path% "%local_path%\MS_Localization.py" ^
log="Export/MS/UWB" ^
out="Export/MS/UWB" ^
arlcl_out="Export/ARLCL/UWB" ^
db="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/DB" ^
batch="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Batch.txt" ^
scenario_id=3 ^
model=uwb ^
seed=3 ^
end_iter=100 ^
opts=300000 ^
learn_rate=1e-2