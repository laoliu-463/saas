# Harness Limits Check

## Active Limits
- Direct files per directory: <= 50
- Direct subdirectories per directory: <= 50
- Non-script text lines per file: <= 200

## Conclusion
FAIL

## Violations
| Path | Issue | Suggestion |
| --- | --- | --- |
| D:\Projects\SAAS\harness\reports | File count over 50 (71) | Archive |
| D:\Projects\SAAS\harness\reports\evidence-20260713-163857.md | Line count over 200 (219) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-164143.md | Line count over 200 (222) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-164605.md | Line count over 200 (223) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-165453.md | Line count over 200 (234) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-165713.md | Line count over 200 (235) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-171027.md | Line count over 200 (228) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-171045.md | Line count over 200 (229) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-173034.md | Line count over 200 (237) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-173150.md | Line count over 200 (233) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-194907.md | Line count over 200 (259) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-194916.md | Line count over 200 (251) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-205830.md | Line count over 200 (253) | Split or truncate |
| D:\Projects\SAAS\harness\reports\evidence-20260713-210450.md | Line count over 200 (261) | Split or truncate |

## Next Steps
Run this check after each task and during weekly or iteration-start cleanup reviews.
