import os, sys, subprocess
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
# Drop test DB first
import psycopg
conn = psycopg.connect(host="localhost", port=5433, dbname="postgres", user="postgres", password="ch@8672786")
conn.autocommit = True
cur = conn.cursor()
cur.execute('SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = \'test_Ruda_Survey\' AND pid <> pg_backend_pid()')
cur.execute('DROP DATABASE IF EXISTS "test_Ruda_Survey"')
conn.close()
print("Test DB dropped.")
# Run tests
backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(backend_dir)
result = subprocess.run(
    [sys.executable, "manage.py", "test", "surveys", "--verbosity=2"],
    capture_output=True, text=True, timeout=600
)
# Print last 50 lines of stdout
lines = result.stdout.splitlines()
for line in lines[-50:]:
    print(line)
# Print last 20 lines of stderr
errlines = result.stderr.splitlines()
for line in errlines[-20:]:
    print(line)
print(f"\nReturn code: {result.returncode}")
