# FixBench

## Description
A benchmark for example-based program transformation with bug-fixing code changes.

## Statistic
| Id |  Bug Type | Bug Description | \# Code Changes |
|:-----------:|:------------------------:|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:------------------------:|
|      1      | Null Pointer Dereference | A dereference-after-store error occurs when a program explicitly sets a pointer to null and dereferences it later.                                                            |            454           |
|      2      |       Out Of Bounds      | Accessing an array with an illegal index that is either negative or greater than or equal to the size of the array.                                                           |            119           |
|      3      |   DM\_DEFAULT\_ENCODING  | A conversion from a byte to String (or String to byte) is using the platform's default encoding. An encoding should be specified instead                                      |            67            |
|      4      |    URF\_UNREAD\_FIELD    | Declared a field but never read/used which implies the code is incomplete. Harmful to code quality and maintenance.                                                           |            36            |
|      5      |  SE\_NO\_SERIALVERSIONID | A class implements the Serializable interface, but does not define a serialVersionUID field.                                                                                  |            84            |
|      6      |     DM\_CONVERT\_CASE    | A String is being converted to upper or lowercase, using the platform's default charset. A charset should be specified instead, otherwise it results in improper conversions. |            141           |
|      7      |   MS\_SHOULD\_BE\_FINAL  | A static field is set to be public but not final, and could be changed by malicious code or by accident from another package.                                                 |            62            |
|       |                          |     Total                                                                                                                                                                          |           1,023          |


