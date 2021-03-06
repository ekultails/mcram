#!/bin/sh
#MCRAM - Cron - mccron.sh

if [[ `uname -s` -eq "Darwin" ]];
        then echo "Mac"
        # size = INPUT_IN_MB * 1024 * 1024 / 512
        size=`expr ${mountRam} \* 1024 \* 1024 / 512`
        tmpPart=`hdid -nomount ram://${size} | grep '/dev/disk'`
        newfs_hfs ${tmpPart}
        mkdir -p $ramlocation
        mount -t hfs ${tmpPart} $ramlocation
else mkdir -p $ramlocation;\
        mount -t tmpfs -o defaults,noatime,size=${mountRam}MB tmpfs $ramlocation;
fi

if [[ ! $(ps aux | grep "$mcstart" | grep java | grep -v grep) ]]
	then #start server
		screen -dmS mcram sh; #creates a screen "mc" running bash for the server
		screen -dmS mcrsync sh; #creates a screen for rsyncing the data
		screen -S mcram -p 0 -X stuff $'rsync -avur $mclocation/ $ramlocation/\n' #rsync into RAM
		screen -S mcram -p 0 -X stuff $'cd $ramlocation/\n' #this allows the server to properly load the eula.txt license agreement
		screen -S mcram -p 0 -X stuff $'java -Xmx${mcstartram}M -Xms${mcstartram}M -jar ./$mcstart nogui\n' #start the server
fi

while [[ 1 -gt 0 ]]; do	
	sleep ${synctime}m;
	screen -p 0 -S mcram -X eval 'stuff "save-all"\015';
	sleep 60;
	screen -S mcrsync -p 0 -X stuff $'rsync -varuP $ramlocation/ $mclocation/; echo "Scheduled sync complete on $(date)."\n' #rsync the data back to the drive
done


