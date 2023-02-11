rem ===== Example for executing a BLE-based localization scenario in headless mode =====
java -jar "arlcl-optimizer.jar" out_path="Export/UWB" db_path="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/DB" batch_path="Examples/UWB-Time_Evaluation-Samples_Engehalde-Floor1 (Examples)/Batch.txt" scenario_id=0 model=uwb contours=30 min_m=60 kn=6 pos_extent=1000 seed=0 end_iter=100 threads=1 opt_iter=1000 max_t=1000000 f_tol=1e-2 step=10 cycles=50

rem ===== Example for executing an UWB-based localization scenario in headless mode =====
rem (to be published)