@echo on
java -cp .;checkmate.jar;lib/jlfgr-1.0.jar;lib/jrandom.jar;lib/log4j-api-2.12.0.jar;lib/log4j-core-2.12.0;lib/xercesImpl-2.12.0.jar;lib/xml-apis-1.4.01.jar;lib/FUSE-1.01.jar checkmate.CMMain %*
