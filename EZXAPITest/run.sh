if [ "$JAVA_HOME" == "" ]; then
   echo JAVA_HOME variable must be set!
   exit
fi
CLASSPATH=bin:
echo $CLASSPATH
for file in lib/*.jar 
do 
   CLASSPATH=$file:$CLASSPATH
done
echo JAVA_HOME=$JAVA_HOME
echo CLASSPATH=$CLASSPATH
export CLASSPATH
$JAVA_HOME/bin/java -cp $CLASSPATH test.TestSockets $1

