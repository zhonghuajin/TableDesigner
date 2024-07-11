from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
import configparser

# 创建一个ConfigParser对象
config = configparser.ConfigParser()

# 读取配置文件
config.read('langchain_config.ini', encoding='utf-8')

# 从配置文件中获取各个配置项
api_key = config.get('settings', 'api_key')
prompt_text = config.get('settings', 'prompt_text')
role = config.get('settings', 'role')
api_base = config.get('settings', 'api_base')
model_name = config.get('settings', 'model_name')

def describe_image( ):

    # 初始化模型
    model = ChatOpenAI(model_name=model_name, openai_api_base=api_base, openai_api_key=api_key)

    with open("image.txt", "r", encoding='utf-8') as f:
        image_data = f.read()

    # 创建包含文本和图片的消息
    human_message = HumanMessage(
        content=[
            {"type": "text", "text": prompt_text},
            {
                "type": "image_url",
                "image_url": {"url": f"data:image/jpeg;base64,{image_data}"},
            },
        ],
    )
    system_message = SystemMessage(content=role)
    
    # 调用模型处理消息
    response = model.invoke([human_message, system_message])
    
    # 打印响应内容
    print(response.content)
    
    # Extract content from the response
    response_lines = response.content.split('\n')
    
    # Remove the first and last lines
    content_to_save = '\n'.join(response_lines[1:-1])
    
    # Save the modified content to a file with UTF-8 encoding
    with open('gpt-gen-sql.txt', 'w', encoding='utf-8') as file:
        file.write(content_to_save)
    

def main():
    describe_image()

if __name__ == "__main__":
    main()