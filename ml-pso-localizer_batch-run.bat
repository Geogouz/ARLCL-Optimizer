rem @echo off

set local_path=%cd%
set venv_path=%local_path%\venv\Scripts\python.exe

rem ####################################################################################################################
rem ===================== Example for executing a BLE-based localization scenario in headless mode =====================
rem ####################################################################################################################
rem %venv_path% "%local_path%\ML-PSO_Localization.py" ^
rem log="Export/ML-PSO/BLE" ^
rem out="Export/ML-PSO/BLE" ^
rem arlcl_out="Export/ARLCL/BLE" ^
rem db="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/DB" ^
rem batch="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Batch.txt" ^
rem scenario_id=0 ^
rem model=ble ^
rem seed=0 ^
rem end_iter=100 ^
rem opts=500 ^
rem particles=500 ^
rem c1=0.7 ^
rem c2=0.3 ^
rem w=0.9

rem exit

rem          or.....
rem ####################################################################################################################
rem ===================== Example for executing an UWB-based localization scenario in headless mode ====================
rem ####################################################################################################################
%venv_path% "%local_path%\ML-PSO_Localization.py" ^
log="Export/ML-PSO/UWB" ^
out="Export/ML-PSO/UWB" ^
arlcl_out="Export/ARLCL/UWB" ^
db="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/DB" ^
batch="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Batch.txt" ^
scenario_id=4 ^
model=uwb ^
seed=1912 ^
end_iter=100 ^
opts=5000 ^
particles=1000 ^
c1=1.4 ^
c2=0.3 ^
w=0.9