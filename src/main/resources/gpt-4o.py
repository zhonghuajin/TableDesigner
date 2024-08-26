from openai import OpenAI
import os
import configparser

# os.environ['http_proxy'] = 'http://127.0.0.1:7890'
# os.environ['https_proxy'] = 'http://127.0.0.1:7890'

# 创建一个ConfigParser对象
config = configparser.ConfigParser()
# 读取配置文件
config.read('langchain_config.ini', encoding='utf-8')
# 从配置文件中获取各个配置项

api_key = config.get('settings', 'api_key')
prompt_text = config.get('settings', 'prompt_text')
role = config.get('settings', 'role')
model_name = config.get('settings', 'model_name')

MODEL=model_name
client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY", api_key))

with open("image.txt", "r", encoding='utf-8') as f:
    image_data = f.read()
response = client.chat.completions.create(
    model=MODEL,
    messages=[
        {"role": "system", "content": role},
        {"role": "user", "content": [
            {"type": "text", "text": prompt_text},
            {"type": "image_url", "image_url": {
                "url": f"data:image/png;base64,{image_data}"}
            }
        ]}
    ],
    temperature=0.0,
)

# Extract content from the response
response_lines = response.choices[0].message.content.split('\n')

# Remove the first and last lines
content_to_save = '\n'.join(response_lines[1:-1])

print(content_to_save)

with open('gpt-gen-sql.txt', 'w', encoding='utf-8') as file:
    file.write(content_to_save)