set SERVICE_NAME=FileSearch
 
REM Service log configuration
set PR_LOGPREFIX=%SERVICE_NAME%
set PR_LOGPATH=%~dp0
set PR_STDOUTPUT=%PR_LOGPATH%\stdout.txt
set PR_STDERROR=%PR_LOGPATH%\stderr.txt
set PR_LOGLEVEL=Debug
 
REM Path to java installation
set PR_JVM=%JAVA_HOME%\bin\server\jvm.dll
set PR_CLASSPATH=%~dp0\lib\*
 
REM Startup configuration
set PR_STARTUP=manual
set PR_STARTMODE=jvm
set PR_STARTCLASS=com.alectenharmsel.indexer.Main
set PR_STARTMETHOD=main
 
REM Shutdown configuration
set PR_STOPMODE=jvm
set PR_STOPCLASS=com.alectenharmsel.indexer.Main
set PR_STOPMETHOD=main
set PR_STOPPARAMS=stop

.\FileSearch //IS/%SERVICE_NAME% --StartParams start ++StartParams file-search.json