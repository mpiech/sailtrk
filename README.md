# sailtrk

Gets sailboat track data for sailcal. Downloads a .json file of tuples of latitude, longitude, timestamp, and other fields representing the track of e.g. a vessel provided by a service such as Marine Traffic. Puts track data in a MongoDB database. Set up to run nightly as a cronjob and email changes. Designed for use with sailcal.

```
git clone https://github.com/mpiech/sailrsv
cd sailrsv

oc project myproj
oc import-image mpiech/s2i-clojure-mail --confirm
# first time build
oc new-build mpiech/s2i-clojure-mail~. --name=sailrsv --env-file=env.cfg
# subsequent rebuilds
oc start-build sailrsv --from-dir=. --follow

# for testing/debugging
# uncomment while's in run.sh and core.lj
oc new-app sailrsv --env-file=env.cfg

# for cronjob
oc create cronjob sailrsv \
--image=image-registry.openshift-image-registry.svc:5000/myproj/sailrsv \
--schedule='05 08 * * *' --restart=Never

############################################################
# env.cfg should specify the following environment variables

TRKDB=
ATLAS_HOST=
ATLAS_USERNAME=
ATLAS_PASSWORD=
ATLAS_DB=
SLCAL_MGUSR=
SLCAL_MGPWD=
SLCAL_MGDB=
TRK_SFTP_PREFIX=
TRK_SFTP_USER=
TRK_TO=
TRK_FROM=
TRK_SUBJECT=
SSMTP_ROOT=
SSMTP_MAILHUB=
SSMTP_AUTHUSER=
SSMTP_AUTHPASS=

```

## License

Copyright © 2014-2022

Distributed under the Eclipse Public License version 1.0 or later.
