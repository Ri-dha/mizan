# 2026-11-18 — Counting API tests after deleting test classes
`target/surefire-reports` keeps the XML of test classes that no longer exist, so summing every
`TEST-*.xml` overstates the suite (it reported 168 when 59 ran). Delete the folder before a run,
or compare against `find src/test -name '*Test.java'`, before quoting a number.
Source: T-iqwealth-fx
