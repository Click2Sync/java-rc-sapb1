# java-rc-sapb1
Java implementation of the reverse connector API of Click2Sync for SAP Business One

## Running

1. Load in eclipse as eclipse project
2. Java 1.8 or later
3. Missing libraries (check .classpath file to understand which libraries to include)
4. Missing sapjco.dll sapbowrapper.jar, etc.

## Notices

- This is the example of a SAPB1 reverse connector implementation for:
	- Products readonly
	- Orders read/write

- But can be implemented also for products read/write if needed, just by calling the right SAP JCO methods

## C2S Reverse Connector Protocol & API Reference

https://www.click2sync.com/developers/reverse.html