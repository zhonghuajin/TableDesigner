# TableDesigner

### 工具简介

TableDesigner是一个配合PowerDesigner 16使用的表设计工具，它监控原型图表单的截图，调用大模型接口对表单截图进行分析并生成sql，  然后通过sql分析工具完成字段分析，最后把分析结果插入到PowerDesigner的PDM文件中，也就是说只需要通过截图操作就完成粗略的表设计。

通过这种自动化工具可以减少使用PowerDesigner进行表设计时的繁琐的字段添加和编辑工作，从而可以有更多的精力可以投放在业务理解中，达到减少设计错误的目的。

### 使用步骤

1. 启动工具（启动前先进行配置，见配置说明部分）
   
   java  -Dfile.encoding=UTF-8   -jar table-designer.jar xxx_
   
   "xxx_"是功能模块涉及的表的表名前缀，比如正在设计会议模块，则使用"meeting_"作为参数。

2. 使用截图工具对原型图中的表单进行截图并保存到剪贴板，比如使用微信的截图功能

如果是第一次截图，稍等片刻之后，当前目录下就会生成名为Table_Design.pdm的文件并自动打开，文件打开后如果发现表设计并不完全符合业务需求则可以进行修改。

如果没发现表设计有什么问题，就可以重复截图操作，以便把其它表单对应的表设计追加到Table_Design.pdm中。但是在对另外一张表单进行截图前请先关掉Table_Design.pdm，因为PowerDesigner不允许其它进程修改当前打开的pdm文件。

#### 使用示例：

![ ](https://raw.githubusercontent.com/zhonghuajin/TableDesigner/master/%E8%A1%A8%E8%AE%BE%E8%AE%A1%E5%B7%A5%E5%85%B7%E7%A4%BA%E4%BE%8B.gif)

### 配置说明

第一次启动前需要在table-designer.jar包同级目录下创建langchain_config.ini配置文件并进行配置。

> 补充说明：
> 
> 本工具基于 Langchain 实现，需要配置LangChain API所需要的基本参数才能让工具正常运行，下面的配置都是针对Langchain接口参数的配置。Langchain封装了多种大模型的接口，调用Langchain的接口相当于调用大模型接口。

#### 基本配置

1. **API密钥 (`api_key`)**:
   
   - `api_key` 是访问 Langchain API 时必需的认证密钥。目前Langchain支持的大模型列表可以参考https://python.langchain.com/v0.2/docs/integrations/chat/；认证密钥可以在淘宝上购买，淘宝搜索“api”，询问商家是否支持gpt-4o接口即可（如需商家推荐可以私我）。
   - 示例: `api_key = sk-CGU5t0NcPGVtzRjg6d2091Aa156d44D2B6Fa77AaDa68EaC2`

2. **提示文本 (`prompt_text`)**:
   
   - `prompt_text` 配置sql生成规则。把最基本的表设计规范描述清楚即可。
   - 示例: `prompt_text = 根据描述进行mysql表设计。...`

3. **角色 (`role`)**:
   
   - `role` 定义用户在使用API时的角色或者身份。良好的角色说明可以显著提升生成的内容质量。
   - 示例: `role = 你是一个精通政企系统开发的专家。`

4. **API基地址 (`api_base`)**:
   
   - 大模型都有基本的接口，`api_base` 是大模型API请求的基础URL地址。
   - 示例: `api_base = https://api.xiaoai.plus/v1`

5. **模型名称 (`model_name`)**:
   
   - `model_name` 指定要使用的Langchain模型的名称。本工具测试使用的是gpt-4o，建议使用gpt-4o，因为别的模型都没有测试过。如果想用其它模型，可能需要二开调试。
   - 示例: `model_name = gpt-4o`

#### 配置示例

以下是一个完整的配置示例：

```plaintext
[settings]
api_key = sk-CGU5t0NcPGVtzRjg6d2091Aa156d44D2B6Fa77AaDa68EaC2
prompt_text = 根据描述进行mysql表设计。使用下划线命名法，主键id使用varchar(50)类型，主键id名称包含表名并以_id结束；comment使用中文，表和字段都有comment；字段不要使用enum、text类型，适合BOOLEAN类型的用tinyint(1)类型；create table使用if not exists语句；不要create_at、update_at等审计字段；不要外键约束。给出sql语句即可，不需要任何解释。
role = 你是一个精通政企系统开发的专家。
api_base = https://api.xiaoai.plus/v1
model_name = gpt-4o
```



### 补充说明

```
        String[][] columnInfo = {
                {"行政区", "ORG_ID", "varchar(50)", null},
                {"创建时间", "CREATE_TIME", "datetime", "CURRENT_TIMESTAMP"},
                {"更新时间", "UPDATE_TIME", "datetime", "CURRENT_TIMESTAMP"},
                {"创建人ID", "CREATE_USER_ID", "varchar(50)", null},
                {"更新人ID", "UPDATE_USER_ID", "varchar(50)", null},
                {"是否已经删除", "IS_DELETED", "tinyint(1)", "0"}
        };


```

上面是com.jim.tabledesigner.Main#addColumns函数中的代码，对应是审计相关的字段。如果希望定制这部分的内容，需要自行修改这部分的代码。