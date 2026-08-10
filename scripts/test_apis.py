import requests
import json

base_url = 'http://127.0.0.1:8070'
task_id = "2084174600277008386"

try:
    # Login
    print("=== 1. 登录 ===")
    resp = requests.post(f'{base_url}/auth/login', json={'username': 'admin', 'password': '123456'}, timeout=5)
    data = resp.json()
    token = data.get('data', {}).get('tokenValue')
    print(f'Token: {token[:30]}...' if token else 'No token')
    
    if token:
        headers = {'Authorization': f'Bearer {token}'}
        
        # Test regenerate API (step 1)
        print('\n=== 2. 测试重新生成 API (step=1) ===')
        try:
            resp = requests.post(f'{base_url}/api/task/{task_id}/regenerate', 
                               params={'stepOrder': 1}, headers=headers, timeout=5)
            print(f'Status: {resp.status_code}')
            print(f'Response: {resp.text[:500]}')
        except Exception as e:
            print(f'Error: {e}')
        
        # Test resume-from-failure API
        print('\n=== 3. 测试断点续跑 API ===')
        try:
            resp = requests.post(f'{base_url}/api/task/{task_id}/resume-from-failure', 
                               headers=headers, timeout=5)
            print(f'Status: {resp.status_code}')
            print(f'Response: {resp.text[:500]}')
        except Exception as e:
            print(f'Error: {e}')
            
except Exception as e:
    print('Error:', e)
    import traceback
    traceback.print_exc()
