from openai import OpenAI
import os
# 设置环境变量
os.environ['http_proxy'] = 'http://127.0.0.1:4780'
os.environ['https_proxy'] = 'http://127.0.0.1:4780'
MODEL="gpt-4o"
client = OpenAI(api_key=os.environ.get("OPENAI_API_KEY", "sk-proj-tB1m0e40DrgKkYHKSZwvT3BlbkFJQpef4KmEJrjIkeCmFdvx"))

with open("image.txt", "r", encoding='utf-8') as f:
    image_data = f.read()
response = client.chat.completions.create(
    model=MODEL,
    messages=[
        {"role": "system", "content": "你是一个精通电子商务系统产品设计和开发的专家"},
        {"role": "user", "content": [
            {"type": "text", "text": "根据描述进行mysql表设计。使用下划线命名法，主键id使用varchar(50)类型，主键id名称包含表名并以_id结束；comment使用中文，表和字段都有comment；字段和表用使用英文命名；字段不要使用enum、text类型，适合BOOLEAN类型的用tinyint(1)类型；create table使用if not exists语句；不要create_at、update_at等审计字段；不要外键约束。给出sql语句即可，不需要任何解释。"},
            {"type": "image_url", "image_url": {
                "url": f"data:image/png;base64,{image_data}"}
            }
        ]}
    ],
    temperature=0.0,
)

print(response.choices[0].message.content)