@echo off
set JAVA_HOME=C:\Users\omita\.jdks\openjdk-22.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
set M2_HOME=C:\Users\omita\Downloads\maven-mvnd-1.0.5-windows-amd64\maven-mvnd-1.0.5-windows-amd64\mvn
set MAVEN_HOME=%M2_HOME%

cd /d "C:\Users\omita\.bob\playground\backend"
"%M2_HOME%\bin\mvn.cmd" test -Dtest=GitServiceTest -B --no-transfer-progress 1> "C:\Users\omita\.bob\playground\mvn-out.txt" 2> "C:\Users\omita\.bob\playground\mvn-err.txt"
echo DONE_EXIT_CODE=%ERRORLEVEL% >> "C:\Users\omita\.bob\playground\mvn-out.txt"
