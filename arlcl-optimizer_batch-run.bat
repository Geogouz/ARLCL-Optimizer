rem @echo off

rem #######################################################################################################
rem ============== Example for executing a BLE-based localization scenario in headless mode ===============
rem #######################################################################################################
java -jar "arlcl-optimizer.jar" ^
out="Export/ARLCL/BLE" ^
db="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/DB" ^
batch="Examples/BLE-RSS_Evaluation-Samples_Lecture-Room (Examples)/Batch.txt" ^
scenario_id=0 ^
model=ble ^
contours=0 ^
min_m=95 ^
kn=6 ^
pos_extent=10 ^
seed=0 ^
end_iter=100 ^
threads=1 ^
opts=1000 ^
max_t=1000000 ^
f_tol=1e-2 ^
step=100 ^
cycles=50

rem exit

rem #######################################################################################################
rem ============== Example for executing an UWB-based localization scenario in headless mode ==============
rem #######################################################################################################
java -jar "arlcl-optimizer.jar" ^
out="Export/ARLCL/UWB" ^
db="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/DB" ^
batch="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Batch.txt" ^
scenario_id=0 ^
model=uwb ^
contours=0 ^
min_m=200 ^
kn=6 ^
pos_extent=1000 ^
seed=0 ^
end_iter=100 ^
threads=1 ^
opts=1000 ^
max_t=1000000 ^
f_tol=1e-2 ^
step=10 ^
cycles=100