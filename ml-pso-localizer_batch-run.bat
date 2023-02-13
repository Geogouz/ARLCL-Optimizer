rem @echo off

set local_path=%cd%
set venv_path=%local_path%\venv\Scripts\python.exe

rem ####################################################################################################################
rem ===================== Example for executing a BLE-based localization scenario in headless mode =====================
rem ####################################################################################################################
%venv_path% "%local_path%\ML-PSO_Localization.py" ^
log="Export/ML-PSO/BLE.log" ^
out="Export/ML-PSO/BLE" ^
arlcl_out="Export/ARLCL/BLE" ^
db="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/DB" ^
batch="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Batch.txt" ^
scenario_id=0 ^
model=ble ^
seed=0 ^
end_iter=100 ^
opts=500 ^
particles=500 ^
c1=0.7 ^
c2=0.3 ^
w=0.9

pause
rem exit

rem          or.....
rem ####################################################################################################################
rem ===================== Example for executing an UWB-based localization scenario in headless mode ====================
rem ####################################################################################################################
%venv_path% "%local_path%\ML-PSO_Localization.py" ^
log="Export/ML-PSO/UWB.log" ^
out="Export/ML-PSO/UWB" ^
arlcl_out="Export/ARLCL/UWB" ^
db="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/DB" ^
batch="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Batch.txt" ^
scenario_id=0 ^
model=uwb
seed=0 ^
end_iter=100 ^
opts=1000 ^
particles=700 ^
c1=0.7 ^
c2=0.3 ^
w=0.9