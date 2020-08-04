# FixBench (All)


## Statistic
| # | Bug Name                        | Count |
|---|---------------------------------|-------|
| 1 | Genesis-NP                      | 475   |
| 2 | FindBugs-DM\_CONVERT\_CASE      | 145   |
| 3 | Genesis-OOB                     | 126   |
| 4 | FindBugs-SE\_NO_SERIALVERSIONID | 84    |
| 5 | FindBugs-DM\_DEFAULT\_ENCODING  | 69    |
| 6 | FindBugs-MS\_SHOULD\_BE\_FINAL  | 62    |
| 7 | FindBugs-URF\_UNREAD\_FIELD     | 62    |
|   | **Total**                           | 1,023 |


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


