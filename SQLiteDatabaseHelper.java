package TableSchemaToKotlinPojo;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteDatabaseHelper {

    public static void main(String[] args) throws IOException {
        String url = "jdbc:sqlite:C:\\Users\\Hrithik\\Downloads\\Newfolder\\MyDatabase.sqlite";
        List<String> tables = getAllTablesNameFromDB(url);

        for (String tableName : tables) {
            System.out.println("Generating Kotlin data class for table: " + tableName + "\n");
            String schema = getTableSchema(url, tableName);
            generateKotlinDataClass(tableName, schema);
        }
    }

    private static List<String> getAllTablesNameFromDB(String sqliteDbUrl) {
        List<String> tablesList = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(sqliteDbUrl)) {
            if (conn != null) {
                DatabaseMetaData meta = conn.getMetaData();
                ResultSet tables = meta.getTables(null, null, null, new String[]{"TABLE"});
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    tablesList.add(tableName);
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return tablesList;
    }

    private static String getTableSchema(String url, String tableName) {
        StringBuilder schema = new StringBuilder();
        try (Connection conn = DriverManager.getConnection(url)) {
            if (conn != null) {
                String query = "PRAGMA table_info(\"" + tableName + "\");";
                Statement statement = conn.createStatement();
                ResultSet resultSet = statement.executeQuery(query);

                while (resultSet.next()) {
                    int cid = resultSet.getInt("cid");
                    String name = resultSet.getString("name");
                    String type = resultSet.getString("type");
                    boolean notNull = resultSet.getInt("notnull") == 1;
                    String defaultValue = resultSet.getString("dflt_value");
                    boolean primaryKey = resultSet.getInt("pk") == 1;

                    // Append column information to schema string
                    schema.append("\"").append(name).append("\" ").append(type);
                    if (notNull) {
                        schema.append(" NOT NULL");
                    }
                    if (defaultValue != null) {
                        schema.append(" DEFAULT ").append(defaultValue);
                    }
                    if (primaryKey) {
                        schema.append(" PRIMARY KEY");
                        if ("INTEGER".equalsIgnoreCase(type)) {
                            schema.append(" AUTOINCREMENT");
                        }
                    }
                    schema.append(",\n");
                }

                resultSet.close();
                statement.close();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return schema.toString();
    }

    private static void generateKotlinDataClass(String tableName, String schema) throws IOException {
        List<String> kotlinDataClassFile = new ArrayList<>(); // to be output

        // adding entity/table name
        StringBuilder tableNameFromSchema = new StringBuilder();
        String[] schemaStringLines = schema.split("\n");
        tableNameFromSchema.append("\"").append(tableName).append("\"");

        // if foreign keys are found
        boolean foreignKeyFound = false;
        for (String line : schemaStringLines) {
            if (line.toLowerCase().contains("constraint") && line.toLowerCase().contains("foreign")) {
                kotlinDataClassFile.add("@Entity(tableName = " + tableNameFromSchema + ",");
                kotlinDataClassFile.add("\tforeignKeys = [");
                foreignKeyFound = true;
                break;
            }
        }

        // if foreign keys are not found
        if (!foreignKeyFound) {
            kotlinDataClassFile.add("@Entity(tableName = " + tableNameFromSchema + ")");
        }

        // adding foreign keys
        for (String foreignKeyLine : schemaStringLines) {
            if (foreignKeyLine.toLowerCase().contains("constraint") && foreignKeyLine.toLowerCase().contains("foreign")) {

                String entityClassName = "";
                String parentColumns = "";
                String childColumns = "";
                String onDelete = "";
                String onUpdate = "";

                if (foreignKeyLine.toLowerCase().contains("on delete cascade")) {
                    onDelete += "CASCADE";
                }
                if (foreignKeyLine.toLowerCase().contains("on update cascade")) {
                    onUpdate += "CASCADE";
                }
                if (foreignKeyLine.toLowerCase().contains("on delete no action")) {
                    onDelete += "NO_ACTION";
                }
                if (foreignKeyLine.toLowerCase().contains("on update no action")) {
                    onUpdate += "NO_ACTION";
                }

                String[] wordsInForeignKeyLine = foreignKeyLine.split(" ");
                int index = 0;
                for (String word : wordsInForeignKeyLine) {
                    // adding childColumns
                    if (word.toLowerCase().startsWith("key")) {
                        String childColumnWithParentheses = word.replaceAll("KEY", "");
                        String childColumnWithoutParentheses = childColumnWithParentheses.replaceAll("[()]", "");
                        childColumns += childColumnWithoutParentheses;
                    }

                    if (word.equalsIgnoreCase("references")) {
                        String parentTableNameOfForeignKey = wordsInForeignKeyLine[index + 1];
                        entityClassName += String.format("{EntityClass for %s}", parentTableNameOfForeignKey.substring(0, parentTableNameOfForeignKey.indexOf("(")));

                        // adding parentColumns
                        String parentColumnsWithParentheses = parentTableNameOfForeignKey.substring(parentTableNameOfForeignKey.indexOf("("));
                        String parentColumnsWithoutParentheses = parentColumnsWithParentheses.replaceAll("[()]", "");
                        parentColumns += parentColumnsWithoutParentheses;
                    }
                    index++;
                }

                String foreignKeyTemplate
                        = String.format("""
                        \t\tForeignKey(
                            \t\tentity = %s::class,
                            \t\tparentColumns = arrayOf(%s),
                            \t\tchildColumns = arrayOf(%s),
                            \t\tonDelete = ForeignKey.%s,
                            \t\tonUpdate = ForeignKey.%s
                        \t\t),""", entityClassName, parentColumns, childColumns, onDelete, onUpdate
                );
                kotlinDataClassFile.add(foreignKeyTemplate);
            }
        }

        // adding bracket after foreign keys
        if (foreignKeyFound) {
            kotlinDataClassFile.add("\t]");
            kotlinDataClassFile.add(")");
        }

        kotlinDataClassFile.add("data class " + tableName + " (");

        StringBuilder primaryKeyColumnName = new StringBuilder();
        boolean isPrimaryKeyAutoInc = false;

        // adding primary key
        for (String line : schemaStringLines) {
            if (line.toLowerCase().contains("primary")) {
                String[] primaryKeyLine = line.split("\\s+");
                for (String word : primaryKeyLine) {
                    if (word.toLowerCase().contains("\"")) {
                        String primaryKeyWithEndQuotes = word.substring(word.indexOf("\"") + 1);

                        String primaryKeyWithoutQuotes = primaryKeyWithEndQuotes.substring(0, primaryKeyWithEndQuotes.indexOf("\""));

                        primaryKeyColumnName.append(primaryKeyWithoutQuotes);
                    }
                    if (word.toLowerCase().contains("autoincrement")) {
                        isPrimaryKeyAutoInc = true;
                    }
                }
            }
        }

        // adding columns
        for (String line : schemaStringLines) {

            if (line.trim().toLowerCase().startsWith("\"") && !line.toLowerCase().contains("constraint")) {

                String dataClassLine = "\n";

                String[] splitLine = line.trim().split("\\s+");

                String columnNameWithQuotes = splitLine[0];
                String columnNameWithoutQuotes = "";
                String columnDataType = "";

                if (columnNameWithQuotesEndsWithQuotes(columnNameWithQuotes)) {
                    columnNameWithoutQuotes += columnNameWithQuotes.substring(1, columnNameWithQuotes.length() - 1);
                } else {
                    columnNameWithoutQuotes += columnNameWithQuotes.substring(1);
                }

                if(!columnNameWithQuotesEndsWithQuotes(columnNameWithQuotes)) {
                    for(int i = 1; i <= splitLine.length; i++) {
                        String word = splitLine[i];
                        if(word.endsWith("\"")) {

                            columnNameWithoutQuotes += "_" + word.substring(0, word.length() - 1);
                            columnNameWithQuotes += " " + word;
                            columnDataType += splitLine[i+1];
                            break;
                        } else {
                            columnNameWithoutQuotes += "_" + word;
                            columnNameWithQuotes += " " + word;
                        }
                    }
                }

                if (columnDataType.isEmpty()) {
                    columnDataType += splitLine[1];
                }

                if (columnNameWithoutQuotes.equalsIgnoreCase(primaryKeyColumnName.toString())) {
                    if (isPrimaryKeyAutoInc) {
                        dataClassLine += "\t@PrimaryKey(autoGenerate = true)\n";
                    } else {
                        dataClassLine += "\t@PrimaryKey\n";
                    }
                }

                String defaultValue = "";
                if (splitLine.length > 2) {
                    for (int i = 0; i < splitLine.length; i++) {
                        if (splitLine[i].equalsIgnoreCase("default")) {
                            String tempDefaultValue = splitLine[i + 1];
                            if (tempDefaultValue.contains(",")) {
                                defaultValue += tempDefaultValue.substring(0, tempDefaultValue.length() - 1);
                            } else {
                                defaultValue = tempDefaultValue;
                            }
                            break;
                        }
                    }
                }

                // variable types
                String variableDataType = "";
                if (columnDataType.toLowerCase().contains("int")) {
                    variableDataType += "Int";
                }
                if (columnDataType.toLowerCase().contains("text")) {
                    variableDataType += "String";
                }
                if (columnDataType.toLowerCase().contains("double")) {
                    variableDataType += "Double";
                }
                if (columnDataType.toLowerCase().contains("char")) {
                    variableDataType += "String";
                }
                if (columnDataType.toLowerCase().contains("date")) {
                    variableDataType += "String";
                }
                if (columnDataType.toLowerCase().contains("real")) {
                    variableDataType += "Double";
                }

                // adding columnInfo
                dataClassLine += "\t@ColumnInfo(name = " + columnNameWithQuotes;

                // adding default value of the column
                if (defaultValue.isEmpty()) {
                    dataClassLine += ")\n";
                } else {
                    dataClassLine += ", defaultValue = \"" + defaultValue + "\")\n";
                }

                // adding class attributes
                dataClassLine += "\t" + "var " + columnNameWithoutQuotes + ": " + variableDataType + "?" + ",";

                if (!dataClassLine.contains(",")) {
                    dataClassLine += ",";
                }
                kotlinDataClassFile.add(dataClassLine);
            }
        }

        kotlinDataClassFile.add(")\n");

        for (String dataClassLine : kotlinDataClassFile) {
            System.out.println(dataClassLine);
        }
        saveDataClassToTxt("C:\\Users\\Hrithik\\Downloads\\GenPojo\\" , tableName, kotlinDataClassFile);
    }

    private static void saveDataClassToTxt(String outputPath, String tableName, List<String> kotlinDataClassFile) throws IOException {
        String fileName = outputPath + tableName + ".txt";
        FileWriter txtFile = new FileWriter(fileName);
        try (BufferedWriter writer = new BufferedWriter(txtFile)) {
            for (String dataClassLine : kotlinDataClassFile) {
                writer.write(dataClassLine);
                writer.newLine();
            }
            System.out.println("Generated Kotlin data class saved to file: " + fileName + "\n\n");
        } catch (IOException e) {
            System.out.println("Error writing file: " + e.getMessage());
        }
    }

    public static boolean columnNameWithQuotesEndsWithQuotes(String columnNameWithQuotes) {
        return columnNameWithQuotes.endsWith("\"");
    }

}
