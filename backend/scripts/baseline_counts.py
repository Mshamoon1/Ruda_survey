import psycopg
pw = open(r'C:\Users\Shamoon\AppData\Local\Temp\opencode\ruda_app_pw.txt').read().strip()
conn = psycopg.connect(dbname='Ruda_Survey', user='ruda_app', password=pw, host='localhost', port=5433)
cur = conn.cursor()
tables = [
    ('survey_master', 'Master rows'),
    ('parcels', 'Parcels'),
    ('survey_changes', 'Revisions'),
    ('survey_images', 'Images'),
    ('audit_logs', 'Audit logs'),
]
for table, label in tables:
    cur.execute(f'SELECT COUNT(*) FROM {table}')
    count = cur.fetchone()[0]
    print(f'{label}: {count}')
conn.close()
