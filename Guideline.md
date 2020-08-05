# Guideline

## Labeling Objective
_Given a code change and a bug type, label whether the code change is a **untangled** and **correct** fix for the bug type or not. If yes, briefly summarize the fix._

- Criteria 1: The code change must be **untangled** which means all the modifications are only made for bug fixing.
- Criteria 2: The code change must be **correct** which means it can be explained why the fix works.

## Example: Fixes for Improper String Comparison

Bug Description

> A String is being converted to upper or lowercase, using the platform's default charset. A charset should be specified instead, otherwise it results in improper conversions.

### Example #1

- Code Change:

```
diff --git a/util/src/main/java/com/psddev/dari/util/FormTag.java b/util/src/main/java/com/psddev/dari/util/FormTag.java
index b473a60a..4d974c93 100644
--- a/util/src/main/java/com/psddev/dari/util/FormTag.java
+++ b/util/src/main/java/com/psddev/dari/util/FormTag.java
@@ -5,0 +6 @@ import java.util.LinkedHashMap;
+import java.util.Locale;
@@ -174 +175 @@ public class FormTag extends TagSupport implements DynamicAttributes {
-                    "method", method.toLowerCase(),
+                    "method", method.toLowerCase(Locale.ENGLISH),
```

- Label: **Yes**
- Reason: The code change is ONLY for bug fixing and specify `Locale.ENGLISH` to `toLowerCase()`.

### Example #2

- Code Change:

```diff
diff --git a/src/main/java/br/com/dbsoft/io/DBSDAO.java b/src/main/java/br/com/dbsoft/io/DBSDAO.java
index 70c275b..6ea36fb 100644
--- a/src/main/java/br/com/dbsoft/io/DBSDAO.java
+++ b/src/main/java/br/com/dbsoft/io/DBSDAO.java
@@ -1173 +1173 @@ public class DBSDAO<DataModelClass> extends DBSDAOBase<DataModelClass> {
-				xMetaData = DBSIO.getTableColumnsMetaData(this.getConnection(), wCommandTableName);
+				xMetaData = DBSIO.getTableColumnsMetaData(this.getConnection(), wCommandTableName.toUpperCase());
```

- Label: **No**
- Reason: The code change only add a call of the method `toUpperCase()` and does not fix the bug.


## Data Folder Structure
```
ROOT
│   Guideline.pdf
└───bug1
│   │   BUG_DESCRIPTION.html   // bug description
│   └───instance1      // named as code change’s unique id
│       │   diff.txt   // diff between buggy and fixed version
│       │   pair.info   // meta information of the code change
│       │   comMsg.txt   // commit message of the code change
│       │   label.csv   // file with label result
│       └───old     // buggy version of the Java file
│       │       │   [OLD_FILE].java
│       └───new     // fixed version of the Java file
│       │       │   [NEW_FILE].java
│   └───instance2
│       │   ...
│   └───   ...
└───bug2
│   └───  ...
└───   ...
```

**Note**:

- Commit messages of the code changes are also provided. Thus, it can also be leveraged as an evidence sometimes. For example, if the commit message already mentions code change is performed with multiple purposes, e.g., _"Bug fix and add new feature..."_, then the code change can be labeled as **No** directly.
- Links like Github issue or pull request which may referenced in commit message are helpful sometimes, because bug is detailed in those Github pages. Thus, you are encouraged to check those pages if metioned.

## Procedure
For each bug type _bug<sub>i</sub>_,

1. Read the bug description in the file _BUG\_DESCRIPTION.html_ to get a basic understanding of natural of _bug<sub>i</sub>_.
2. Follow the sequence of code change ID in _label.csv_, check each of them successively. For each code change _C<sub>i</sub>_,
	1. Read the diff file _diff.txt_ to know the code changes of _C<sub>i</sub>_.
	2. Identify if _C<sub>i</sub>_ is untangled or not. (see criteria as follow).
	3. Record the result in _label.csv_. Also, briefly write down the reason for your judgment. Especially if it is untangled, briefly describe the solution. **Label as `U` (short for "Uncertain") if unsure**.

**Note**, please take at least 10-mins break for every half an hour to mitigate tiredness factor.