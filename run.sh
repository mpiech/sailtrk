#!/bin/bash

export USER_ID=$(id -u)
export GROUP_ID=$(id -g)
envsubst < ${HOME}/src/resources/passwd.template > /tmp/passwd
export LD_PRELOAD=libnss_wrapper.so
export NSS_WRAPPER_PASSWD=/tmp/passwd
export NSS_WRAPPER_GROUP=/etc/group

echo "hostname=$HOSTNAME" >> /etc/ssmtp/ssmtp.conf

TMPDATE=`date "+%m.%d"`
TMPFILE=/tmp/myscallog.$TMPDATE

echo "To: $TRK_TO" > $TMPFILE
echo "From: $TRK_FROM" >> $TMPFILE
echo "Subject: $TRK_SUBJECT $TMPDATE" >> $TMPFILE
echo "" >> $TMPFILE

echo "----> `date \"+%H:%M:%S\"` Starting sailtrk" &>> $TMPFILE

echo -----curl----- &>> $TMPFILE

curl -o /tmp/sailtrk_`date +%Y%m%d`.json.gz ${TRK_SFTP_PREFIX}`date +%Y%m%d`.json.gz -k -u $TRK_SFTP_USER &>> $TMPFILE

echo -----gunzip----- &>> $TMPFILE

if [ -f /tmp/sailtrk_`date +%Y%m%d`.json.gz ]; then
    gunzip -f /tmp/sailtrk_`date +%Y%m%d`.json.gz &>> $TMPFILE

    echo -----sailtrack----- &>> $TMPFILE
    
    nohup java -jar $HOME/app-standalone.jar /tmp/sailtrk_`date +%Y%m%d`.json &>> $TMPFILE
    
else
    echo "no track file found" &>> $TMPFILE
fi

echo "----> `date \"+%H:%M:%S\"` Finished sailtrk" &>> $TMPFILE

cat $TMPFILE | ssmtp $TRK_TO

# uncomment below when running as app rather than cronjob to debug
#while true; do sleep 2; done
