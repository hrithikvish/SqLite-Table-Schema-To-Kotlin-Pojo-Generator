package TableSchemaToKotlinPojo;

import java.util.*;

public class TableSchemaToKotlinPojo {
    public static void main(String[] args) {

        StringBuilder schemaString = new StringBuilder();
        String tableName = ""; // Class Name

        // taking table schema as input
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter class name: ");
        tableName += sc.nextLine();

        System.out.print("Enter schema: ");
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            if (line.isEmpty()) {
                break;
            }
            schemaString.append(line).append("\n");
        }
        sc.close();

        String[] schemaStringLines = schemaString.toString().split("\n");
        List<String> kotlinDataClassFile = new ArrayList<>(); // to be output

        // adding entity/table name
        StringBuilder tableNameFromSchema = new StringBuilder();
        String createTableLineInSchema = schemaStringLines[0];
        String[] wordsInCreateTableLine = createTableLineInSchema.split(" ");
        for (String word : wordsInCreateTableLine) {
            if (word.startsWith("\"")) {
                tableNameFromSchema.append(word);
            }
        }

        boolean foreignKeyFound = false;
        for (String line : schemaStringLines) {
            if (line.toLowerCase().contains("constraint") && line.toLowerCase().contains("foreign")) {
                kotlinDataClassFile.add("@Entity(tableName = " + tableNameFromSchema + ",");
                kotlinDataClassFile.add("\tforeignKeys = [");
                foreignKeyFound = true;
                break;
            }
        }
        if (!foreignKeyFound) {
            kotlinDataClassFile.add("@Entity(tableName = " + tableNameFromSchema + ")");
        }

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

                System.out.println(columnDataType);

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

                dataClassLine += "\t@ColumnInfo(name = " + columnNameWithQuotes;

                if (defaultValue.isEmpty()) {
                    dataClassLine += ")\n";
                } else {
                    dataClassLine += ", defaultValue = \"" + defaultValue + "\")\n";
                }

                dataClassLine += "\t" + "var " + columnNameWithoutQuotes + ": " + variableDataType + "?" + ",";

                if (!dataClassLine.contains(",")) {
                    dataClassLine += ",";
                }
                kotlinDataClassFile.add(dataClassLine);
            }
        }

        kotlinDataClassFile.add(")");

        for (String dataClassLine : kotlinDataClassFile) {
            System.out.println(dataClassLine);
        }

    }

    public static boolean columnNameWithQuotesEndsWithQuotes(String columnNameWithQuotes) {
        return columnNameWithQuotes.endsWith("\"");
    }
}