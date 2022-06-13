# FixBench (Within Single Method)


## Statistic
| # | Bug name                       | Count |
|---|--------------------------------|-------|
| 1 | Genesis-NP                     | 374   |
| 2 | Genesis-OOB                    | 104   |
| 3 | FindBugs-DM\_CONVERT\_CASE     | 34    |
| 4 | FindBugs-DM\_DEFAULT\_ENCODING | 16    |
|   | Total                          | 528   |



## Directory Structure
```
ROOT
│   README.md
└───bug1
│   │   BUG_DESCRIPTION.html   // bug description
│   └───instance1      // named as code change’s unique id
│       │   diff.txt   // diff between buggy and fixed version
│       │   pair.info   // meta information of the code change
│       │   comMsg.txt   // commit message of the code change
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


