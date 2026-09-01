"""Drop the test database using psycopg3 directly."""
import psycopg

conn = psycopg.connect(
    host="localhost", port=5433, dbname="postgres", user="postgres",
    password="ch@8672786",
)
conn.autocommit = True
cur = conn.cursor()

# Terminate connections
cur.execute("""
    SELECT pg_terminate_backend(pid) FROM pg_stat_activity 
    WHERE datname = 'test_Ruda_Survey' AND pid <> pg_backend_pid()
""")
print("Terminated:", cur.fetchall())

# Drop
cur.execute('DROP DATABASE IF EXISTS "test_Ruda_Survey"')
print("DROP:", cur.statusmessage)

# Verify
cur.execute("SELECT datname FROM pg_database")
print("Remaining:", [r[0] for r in cur.fetchall()])
conn.close()
