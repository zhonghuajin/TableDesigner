package com.jim.tabledesigner;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {

    private static List<Element> newTables = new ArrayList<>();
    private static String[] argsGlobal;

    private static Scanner scanner = new Scanner(System.in);

    // ANSI 转义码，用于设置文本颜色
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";

    public static void main(String[] args) throws IOException {

        /**
         * 如果第一个参数不是以 .pdm 结尾，则说明用户没有传入文件名，需要手动输入
         * 第二个参数是所有表名的前缀
         */
        String[] newArgs;
        if (!args[0].endsWith(".pdm")) {
            newArgs = new String[args.length + 1];  // 创建一个新数组，长度比原始 args 长 1
            newArgs[0] = ensureFilePresent();       // 将新参数放在新数组的第一个位置
            System.arraycopy(args, 0, newArgs, 1, args.length);  // 复制原始 args 到新数组
        } else {
            newArgs = args;
        }

        argsGlobal = newArgs;

        ClipboardWatcher watcher = new ClipboardWatcher();
        watcher.startMonitoring();

        boolean continueLoop = true;

        while (continueLoop) {

            try {
                System.out.println(ANSI_RED + "正在监听剪贴板\r\n按任意键退出程序..." + ANSI_RESET);

                // 等待用户输入任意行
                scanner.nextLine();

                // 关闭 Scanner 对象
                scanner.close();

                System.out.println("程序已退出。");

                // 退出程序
                System.exit(0);

            } catch (NumberFormatException e) {
                System.err.println("Invalid input: Please enter a number.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        scanner.close();
    }
    private static InputStream getDefaultResourceAsStream() {
        // 获取类加载器
        ClassLoader classLoader = com.jim.tabledesigner.Main.class.getClassLoader();
        // 获取资源的URL
        java.net.URL resource = classLoader.getResource("Table_Design.pdm");
        if (resource == null) {
            throw new IllegalArgumentException("未找到默认资源文件");
        }
        // 打开一个输入流到这个资源
        try {
            return resource.openStream();
        } catch (IOException e) {
            throw new IllegalArgumentException("资源路径错误", e);
        }
    }

    private static String ensureFilePresent() throws IOException {
        File currentDirectory = new File(".");
        File[] pdmFiles = currentDirectory.listFiles((dir, name) -> name.endsWith(".pdm"));

        if (pdmFiles == null || pdmFiles.length == 0) {
            // 没有.pdm文件，复制默认资源文件到当前目录
            InputStream defaultResourceStream = getDefaultResourceAsStream();
            File destFile = new File(currentDirectory, "Table_Design.pdm");
            Files.copy(defaultResourceStream, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // 关闭流
            defaultResourceStream.close();
            return destFile.getAbsolutePath();
        } else {
            // 使用当前目录中的第一个.pdm文件
            return pdmFiles[0].getAbsolutePath();
        }
    }


    // pdm中新增表
    public static void processNewTable() throws DocumentException, JSQLParserException, IOException {
        addTable(argsGlobal);
        assignCommentToColumnName(argsGlobal);
        assignCommentToTableName(argsGlobal);
        addPrefixToTableNames(argsGlobal);
        addAdditionalColumns(argsGlobal, true);
        setTinyintColumnsNotNullAndDefault0(argsGlobal);

        // 打印新增的表的表名
        for (Element newTable : newTables) {
            System.out.println(newTable.element(new QName("Code", new Namespace("a", "attribute"))).getText() + " 表已添加");
        }

        // 清空newTables
        newTables.clear();

        if (argsGlobal.length < 1) {
            throw new IllegalArgumentException("第一个参数必须是pdm文件路径");
        }
        String fileName = argsGlobal[0];

        // 用默认的程序打开该类型的文件
        Desktop.getDesktop().open(new File(fileName));


    }


    static void insertOrgIdBeforeCreateTime(List<Element> tableEles, Namespace oNamespace, Namespace aNamespace, Namespace cNamespace) {
        for (Element table : tableEles) {
            List<Element> columns = table.element(new QName("Columns", cNamespace)).elements(new QName("Column", oNamespace));
            int createTimeIndex = -1;
            for (int i = 0; i < columns.size(); i++) {
                Element column = columns.get(i);
                String code = getTextFromEle(column.element(new QName("Code", aNamespace)));
                if ("CREATE_TIME".equals(code)) {
                    createTimeIndex = i;
                    break;
                }
            }
            if (createTimeIndex != -1) {
                Element newColumn = DocumentHelper.createElement(new QName("Column", oNamespace));
                newColumn.addElement(new QName("Name", aNamespace)).setText("行政级别");
                newColumn.addElement(new QName("Code", aNamespace)).setText("ORG_ID");
                newColumn.addElement(new QName("DataType", aNamespace)).setText("varchar(50)");
                columns.add(createTimeIndex, newColumn);
            }
        }
    }

    // 添加审计字段
    public static void addAdditionalColumns(String[] args, boolean addAdditionalColumns) throws DocumentException, IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("第一个参数必须是pdm文件路径");
        }
        String fileName = args[0];
        SAXReader saxReader = new SAXReader();
        Document document = saxReader.read(new File(fileName));
        Element rootElement = document.getRootElement();

        Namespace oNamespace = new Namespace("o", "object");
        Namespace cNamespace = new Namespace("c", "collection");
        Namespace aNamespace = new Namespace("a", "attribute");

        Element rootObject = rootElement.element(new QName("RootObject", oNamespace));
        Element children = rootObject.element(new QName("Children", cNamespace));
        Element model = children.element(new QName("Model", oNamespace));
        List<Element> tableEles = model.element(new QName("Tables", cNamespace)).elements(new QName("Table", oNamespace));

        if (addAdditionalColumns) {
            insertAdditionalColumns(tableEles, oNamespace, aNamespace, cNamespace);
        } else {
            insertOrgIdBeforeCreateTime(tableEles, oNamespace, aNamespace, cNamespace);
        }

        File pdmFile = new File(fileName);
        XMLWriter writer = new XMLWriter(new FileOutputStream(pdmFile));
        writer.write(document);
        writer.close();
    }

    static void insertAdditionalColumns(List<Element> tableEles, Namespace oNamespace, Namespace aNamespace, Namespace cNamespace) {
        for (Element table : tableEles) {
            List<Element> columns = table.element(new QName("Columns", cNamespace)).elements(new QName("Column", oNamespace));
            addColumns(columns, oNamespace, aNamespace);
        }
        return;

    }

    static void addColumns(List<Element> columns, Namespace oNamespace, Namespace aNamespace) {
        // 定义列的基本信息
        String[][] columnInfo = {
                {"行政区", "ORG_ID", "varchar(50)", null},
                {"创建时间", "CREATE_TIME", "datetime", "CURRENT_TIMESTAMP"},
                {"更新时间", "UPDATE_TIME", "datetime", "CURRENT_TIMESTAMP"},
                {"创建人ID", "CREATE_USER_ID", "varchar(50)", null},
                {"更新人ID", "UPDATE_USER_ID", "varchar(50)", null},
                {"是否已经删除", "IS_DELETED", "tinyint(1)", "0"}
        };

        // 遍历并创建每个列
        for (String[] info : columnInfo) {
            // 检查列是否已经存在
            boolean exists = columns.stream().anyMatch(c -> {
                Element codeElement = c.element(new QName("Code", aNamespace));
                return codeElement != null && codeElement.getTextTrim().equals(info[1]);
            });

            if (!exists) {
                Element column = DocumentHelper.createElement(new QName("Column", oNamespace));
                column.addElement(new QName("Name", aNamespace)).setText(info[0]);
                column.addElement(new QName("Code", aNamespace)).setText(info[1]);
                column.addElement(new QName("DataType", aNamespace)).setText(info[2]);

                // 设置默认值
                if (info[3] != null) {
                    column.addElement(new QName("DefaultValue", aNamespace)).setText(info[3]);
                }

                Element comment = DocumentHelper.createElement(new QName("Comment", oNamespace));
                comment.setText(info[0]);
                column.addText("\n");
                columns.add(column);
            }
        }
    }


    // 把列的注释赋值给列名
    public static void assignCommentToColumnName(String[] args) throws DocumentException, IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("第一个参数必须是pdm文件路径");
        }
        String fileName = args[0];
        SAXReader saxReader = new SAXReader();
        Document document = saxReader.read(new File(fileName));
        Element rootElement = document.getRootElement();

        Namespace oNamespace = new Namespace("o", "object");
        Namespace cNamespace = new Namespace("c", "collection");
        Namespace aNamespace = new Namespace("a", "attribute");

        Element rootObject = rootElement.element(new QName("RootObject", oNamespace));
        Element children = rootObject.element(new QName("Children", cNamespace));
        Element model = children.element(new QName("Model", oNamespace));
        List<Element> tableEles = model.element(new QName("Tables", cNamespace)).elements(new QName("Table", oNamespace));

        assignCommentToColumnName(tableEles, cNamespace, aNamespace, oNamespace);

        // 保存修改后的PDM文件
        XMLWriter writer = new XMLWriter(new FileOutputStream(fileName));
        writer.write(document);
        writer.close();
    }

    static void assignCommentToColumnName(List<Element> tableEles, Namespace cNamespace, Namespace aNamespace, Namespace oNamespace) {
        for (Element table : tableEles) {
            List<Element> columns = table.element(new QName("Columns", cNamespace)).elements(new QName("Column", oNamespace));
            for (Element column : columns) {
                Element commentElement = column.element(new QName("Comment", aNamespace));
                if (commentElement != null) {
                    String comment = commentElement.getTextTrim();
                    // 判断comment是否为空或只包含空白字符
                    if (comment == null || comment.isEmpty()) {
                        continue; // 跳过当前循环的剩余部分
                    }
                    // 使用正则表达式找到第一个标点符号前的内容
                    Matcher matcher = Pattern.compile("^(.*?)[,.;:!?\\s，。、；：！？]").matcher(comment);
                    if (matcher.find()) {
                        String nameBeforePunctuation = matcher.group(1);
                        // 赋值给name标签
                        Element nameElement = column.element(new QName("Name", aNamespace));
                        if (nameElement != null) {
                            nameElement.setText(nameBeforePunctuation);
                        }
                    } else {
                        // 如果没有找到标点符号，直接将comment赋值给name
                        Element nameElement = column.element(new QName("Name", aNamespace));
                        if (nameElement != null) {
                            nameElement.setText(comment);
                        }
                    }
                }
            }
        }
    }

    // 为表名添加前缀，前缀来源于命令行参数
    public static void addPrefixToTableNames(String[] args) throws DocumentException, IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("需要两个参数：pdm文件路径和表名前缀");
        }
        String fileName = args[0];
        String prefix = args[1];
        SAXReader saxReader = new SAXReader();
        Document document = saxReader.read(new File(fileName));
        Element rootElement = document.getRootElement();

        Namespace oNamespace = new Namespace("o", "object");
        Namespace cNamespace = new Namespace("c", "collection");
        Namespace aNamespace = new Namespace("a", "attribute");

        Element rootObject = rootElement.element(new QName("RootObject", oNamespace));
        Element children = rootObject.element(new QName("Children", cNamespace));
        Element model = children.element(new QName("Model", oNamespace));
        List<Element> tableEles = model.element(new QName("Tables", cNamespace)).elements(new QName("Table", oNamespace));

        addPrefixToTableNames(tableEles, aNamespace, prefix);

        // 保存修改后的PDM文件
        XMLWriter writer = new XMLWriter(new FileOutputStream(fileName));
        writer.write(document);
        writer.close();
    }

    static void addPrefixToTableNames(List<Element> tableEles, Namespace aNamespace, String prefix) {
        for (Element table : tableEles) {
            Element codeElement = table.element(new QName("Code", aNamespace));
            if (codeElement != null) {
                String originalCode = codeElement.getTextTrim();
                // 判断是否已经有前缀，不区分大小写
                if (originalCode.toLowerCase().startsWith(prefix.toLowerCase())) {
                    continue;
                }
                // prefix转成大写
                prefix = prefix.toUpperCase();
                codeElement.setText(prefix + originalCode);
            }
        }
    }

    // 把表的注释赋值给表名
    public static void assignCommentToTableName(String[] args) throws DocumentException, IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("第一个参数必须是pdm文件路径");
        }
        String fileName = args[0];
        SAXReader saxReader = new SAXReader();
        Document document = saxReader.read(new File(fileName));
        Element rootElement = document.getRootElement();

        Namespace oNamespace = new Namespace("o", "object");
        Namespace cNamespace = new Namespace("c", "collection");
        Namespace aNamespace = new Namespace("a", "attribute");

        Element rootObject = rootElement.element(new QName("RootObject", oNamespace));
        Element children = rootObject.element(new QName("Children", cNamespace));
        Element model = children.element(new QName("Model", oNamespace));
        List<Element> tableEles = model.element(new QName("Tables", cNamespace)).elements(new QName("Table", oNamespace));

        assignCommentToTableName(tableEles, aNamespace);

        // 保存修改后的PDM文件
        XMLWriter writer = new XMLWriter(new FileOutputStream(fileName));
        writer.write(document);
        writer.close();
    }

    static void assignCommentToTableName(List<Element> tableEles, Namespace aNamespace) {
        for (Element table : tableEles) {
            Element commentElement = table.element(new QName("Comment", aNamespace));
            if (commentElement != null) {
                String comment = commentElement.getTextTrim();
                // 检查comment是否为空
                if (comment.isEmpty()) {
                    continue; // 如果comment为空，跳过当前循环
                }

                // 使用正则表达式找到第一个标点符号前的内容
                Matcher matcher = Pattern.compile("^(.*?)[,.;:!?\\s，。、；：！？]").matcher(comment);
                String nameToSet = comment; // 默认为整个评论内容

                if (matcher.find()) {
                    nameToSet = matcher.group(1); // 如果找到标点符号，使用标点符号前的文本
                }

                Element nameElement = table.element(new QName("Name", aNamespace));
                if (nameElement != null) {
                    nameElement.setText(nameToSet);
                }
            }
        }
    }

    static String getTextFromEle(Element element) {
        if (element == null) {
            return "";
        }
        return element.getText();
    }

    public static void supplementaryComment(String[] args) throws DocumentException, IOException {
        String fileName = args[0];
        File pdmFile = new File(fileName);
        if (!pdmFile.exists() || !pdmFile.isFile()) {
            System.err.println("提供的文件路径不是一个有效的文件。");
            return;
        }

        SAXReader saxReader = new SAXReader();
        Document document = saxReader.read(pdmFile);
        Element rootElement = document.getRootElement();

        Namespace oNamespace = new Namespace("o", "object");
        Namespace cNamespace = new Namespace("c", "collection");
        Namespace aNamespace = new Namespace("a", "attribute");

        Element rootObject = rootElement.element(new QName("RootObject", oNamespace));
        Element children = rootObject.element(new QName("Children", cNamespace));
        Element model = children.element(new QName("Model", oNamespace));
        List<Element> tableEles = model.element(new QName("Tables", cNamespace)).elements(new QName("Table", oNamespace));

        addMissingCommentsToColumns(tableEles, oNamespace, aNamespace, cNamespace);

        // 保存修改后的PDM文件
        XMLWriter writer = new XMLWriter(new FileOutputStream(pdmFile));
        writer.write(document);
        writer.close();
    }

    static void addMissingCommentsToColumns(List<Element> tableEles, Namespace oNamespace, Namespace aNamespace, Namespace cNamespace) {
        for (Element table : tableEles) {
            List<Element> columns = table.element(new QName("Columns", cNamespace)).elements(new QName("Column", oNamespace));
            for (Element column : columns) {
                Element nameElement = column.element(new QName("Name", aNamespace));
                Element commentElement = column.element(new QName("Comment", aNamespace));
                if (nameElement != null && (commentElement == null || commentElement.getTextTrim().isEmpty())) {
                    String nameText = nameElement.getTextTrim();
                    if (commentElement == null) {
                        commentElement = column.addElement(new QName("Comment", aNamespace));
                    }
                    commentElement.setText(nameText);
                }
            }
        }
    }

    // 设置所有tinyint类型的列为非空且默认值为0
    public static void setTinyintColumnsNotNullAndDefault0(String[] args) throws DocumentException, IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("第一个参数必须是pdm文件路径");
        }
        String fileName = args[0];
        SAXReader saxReader = new SAXReader();
        Document document = saxReader.read(new File(fileName));
        Element rootElement = document.getRootElement();

        Namespace oNamespace = new Namespace("o", "object");
        Namespace cNamespace = new Namespace("c", "collection");
        Namespace aNamespace = new Namespace("a", "attribute");

        Element rootObject = rootElement.element(new QName("RootObject", oNamespace));
        Element children = rootObject.element(new QName("Children", cNamespace));
        Element model = children.element(new QName("Model", oNamespace));
        List<Element> tableEles = model.element(new QName("Tables", cNamespace)).elements(new QName("Table", oNamespace));

        setTinyintColumnsNotNullAndDefault0(tableEles, oNamespace, aNamespace, cNamespace);

        // 保存修改后的PDM文件
        XMLWriter writer = new XMLWriter(new FileOutputStream(fileName));
        writer.write(document);
        writer.close();
    }

    static void setTinyintColumnsNotNullAndDefault0(List<Element> tableEles, Namespace oNamespace, Namespace aNamespace, Namespace cNamespace) {
        for (Element table : tableEles) {
            List<Element> columns = table.element(new QName("Columns", cNamespace)).elements(new QName("Column", oNamespace));
            for (Element column : columns) {
                Element dataTypeElement = column.element(new QName("DataType", aNamespace));
                if (dataTypeElement != null && "tinyint(1)".equalsIgnoreCase(dataTypeElement.getTextTrim())) {
                    Element nullableElement = column.element(new QName("Nullable", aNamespace));
                    if (nullableElement == null) {
                        nullableElement = column.addElement(new QName("Nullable", aNamespace));
                    }
                    nullableElement.setText("false");

                    Element defaultElement = column.element(new QName("DefaultValue", aNamespace));
                    if (defaultElement == null) {
                        defaultElement = column.addElement(new QName("DefaultValue", aNamespace));
                    }
                    defaultElement.setText("0");
                }
            }
        }
    }

    /**
     * 删除指定名称的列。
     *
     * @param tableEles  表元素列表
     * @param aNamespace 属性的命名空间
     * @param columnName 要删除的列名
     */
    static void removeColumnsByName(List<Element> tableEles, Namespace oNamespace, Namespace aNamespace, Namespace cNamespace, String columnName) {
        for (Element table : tableEles) {
            List<Element> columns = table.element(new QName("Columns", cNamespace)).elements(new QName("Column", oNamespace));
            Iterator<Element> iterator = columns.iterator();
            while (iterator.hasNext()) {
                Element column = iterator.next();
                Element nameElement = column.element(new QName("Name", aNamespace));
                if (nameElement != null && columnName.equals(nameElement.getTextTrim())) {
                    iterator.remove();  // 删除匹配的列
                }
            }
        }
    }


    /**
     * 尝试解析SQL语句，并在成功时返回true。
     *
     * @param sql 要解析的SQL语句。
     * @return 如果SQL语句成功解析，则返回true；否则返回false。
     */
    public static boolean tryParseSQL(String sql) {
        try {
            // 使用jsqlparser解析SQL语句
            Statements statements = CCJSqlParserUtil.parseStatements(sql);
            // 解析成功，返回true
            return true;
        } catch (JSQLParserException e) {
            // 发生异常，返回false
            return false;
        }
    }

    /**
     * 向PDM文件中增加一张表
     *
     * @param args
     * @throws DocumentException
     * @throws IOException
     */
    static void addTable(String[] args) throws DocumentException, IOException, JSQLParserException {
        if (args.length < 1) {
            throw new IllegalArgumentException("第一个参数必须是pdm文件路径");
        }
        String fileName = args[0];
        SAXReader saxReader = new SAXReader();
        Document document = saxReader.read(new File(fileName));
        Namespace oNamespace = new Namespace("o", "object");
        Namespace cNamespace = new Namespace("c", "collection");
        Namespace aNamespace = new Namespace("a", "attribute");

        // 读取addTable.sql文件内容
        File sqlFile = new File("addTable.sql");
        StringBuilder sqlBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(sqlFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sqlBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("无法读取addTable.sql文件: " + e.getMessage());

        }

        String sql = sqlBuilder.toString();
        // 解析SQL语句
        Statements statements = CCJSqlParserUtil.parseStatements(sql);
        for (Statement statement : statements.getStatements()) {
            if (statement instanceof CreateTable) {
                CreateTable createTable = (CreateTable) statement;
                // 初始化默认值
                String tableComment = extractTableComment(createTable);

                // 如果tableComment为空，就用表名作为注释
                if (tableComment.isEmpty()) {
                    tableComment = createTable.getTable().getName();
                } else {
                    tableComment = tableComment.replace("'", "").replace("\"", "");
                }

                addTable(createTable, document, tableComment, oNamespace, aNamespace, cNamespace);
            }
        }

        // 保存修改后的PDM文件
        XMLWriter writer = new XMLWriter(new FileOutputStream(fileName));
        writer.write(document);
        writer.close();
    }

    public static String extractTableComment(CreateTable createTable) {
        if (createTable.getTableOptionsStrings() != null) {
            // 获取表选项字符串列表
            List<String> options = createTable.getTableOptionsStrings();
            // 将所有选项转换为小写
            List<String> lowerCaseOptions = new ArrayList<>();
            for (String option : options) {
                lowerCaseOptions.add(option.toLowerCase());
            }
            // 查找 "COMMENT" 关键词的位置
            int commentIndex = lowerCaseOptions.indexOf("comment");
            if (commentIndex != -1 && commentIndex + 1 < options.size()) {
                // 判断options.get(commentIndex + 1)是否包含等于号
                String comment = options.get(commentIndex + 1);
                if (comment.contains("=")) {
                    // 如果包含等于号，就取等于号后面的部分
                    return options.get(commentIndex + 2);
                } else {
                    // 如果不包含等于号，就直接返回
                    return comment;
                }
            }
        }
        // 如果没有找到注释或表选项为 null，返回空字符串
        return "";
    }

    public static void addTable(CreateTable createTableStatement, Document document, String tableComment, Namespace oNamespace, Namespace aNamespace, Namespace cNamespace) {

        Table table = createTableStatement.getTable();
        List<ColumnDefinition> columnsDefinitions = createTableStatement.getColumnDefinitions();

        Element rootElement = document.getRootElement();
        Element rootObject = rootElement.element(new QName("RootObject", oNamespace));
        Element children = rootObject.element(new QName("Children", cNamespace));
        Element model = children.element(new QName("Model", oNamespace));
        List<Element> tableEles = model.element(new QName("Tables", cNamespace)).elements(new QName("Table", oNamespace));

        String tableName = table.getName().replace("`", "").replace("\"", "");
        Element newTable = DocumentHelper.createElement(new QName("Table", oNamespace));
        newTable.addElement(new QName("Name", aNamespace)).setText(tableName);
        newTable.addElement(new QName("Code", aNamespace)).setText(tableName.toUpperCase());
        newTable.addElement(new QName("Comment", aNamespace)).setText(tableComment);

        Element columns = newTable.addElement(new QName("Columns", cNamespace));


        for (ColumnDefinition columnDefinition : columnsDefinitions) {
            String columnName = columnDefinition.getColumnName().replace("`", "").replace("\"", "");
            String dataType = columnDefinition.getColDataType().toString();
//            String constraints = columnDefinition.getColumnSpecs() != null ? String.join(" ", columnDefinition.getColumnSpecs()) : "";
            String comment = extractComment(columnDefinition.getColumnSpecs()).replace("'", "").replace("\"", "");
            String defaultValue = extractDefaultValue(columnDefinition.getColumnSpecs());
            addColumn(columns, columnName.toUpperCase(), comment, dataType.toUpperCase(), "", defaultValue, oNamespace, aNamespace);
        }

        tableEles.add(newTable);
        newTable.addText("\n");

        newTables.add(newTable);
    }

    private static String extractDefaultValue(List<String> columnSpecs) {
        if (columnSpecs == null) {
            return "";
        }
        // Convert all items in columnSpecs to lowercase to handle case insensitivity
        List<String> lowerCaseSpecs = columnSpecs.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        int defaultIdx = lowerCaseSpecs.indexOf("default");
        if (defaultIdx != -1 && defaultIdx + 1 < columnSpecs.size()) {
            return columnSpecs.get(defaultIdx + 1);  // Return the original case of the value
        }
        return "";
    }

    private static String extractComment(List<String> columnSpecs) {
        if (columnSpecs == null) {
            return "";
        }
        // Convert all items in columnSpecs to lowercase for case-insensitive search
        List<String> lowerCaseSpecs = columnSpecs.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        int commentIdx = lowerCaseSpecs.indexOf("comment");
        if (commentIdx != -1 && commentIdx + 1 < columnSpecs.size()) {
            // Return the original case of the comment to preserve any specific casing
            return columnSpecs.get(commentIdx + 1);
        }
        return "";
    }

    /**
     * 处理pdm的外键和主键约束都比较麻烦，现在的代码不支持，只能手动添加，所以constraints参数暂时不用
     *
     * @param columns
     * @param code
     * @param comment
     * @param dataType
     * @param constraints
     * @param defaultValue
     * @param oNamespace
     * @param aNamespace
     */
    static void addColumn(Element columns, String code, String comment, String dataType, String constraints, String defaultValue, Namespace oNamespace, Namespace aNamespace) {
        Element column = DocumentHelper.createElement(new QName("Column", oNamespace));
        column.addElement(new QName("Name", aNamespace)).setText(code);
        column.addElement(new QName("Code", aNamespace)).setText(code);
        column.addElement(new QName("DataType", aNamespace)).setText(dataType);
        column.addElement(new QName("Comment", aNamespace)).setText(comment);

        if (!constraints.isEmpty()) {
            column.addElement(new QName("Constraints", aNamespace)).setText(constraints);
        }

        // 设置默认值
        if (defaultValue != null && !defaultValue.isEmpty()) {
            column.addElement(new QName("DefaultValue", aNamespace)).setText(defaultValue);
        }

        columns.add(column);
        columns.addText("\n"); // 增加换行以提高可读性
    }

}
