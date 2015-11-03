#!/bin/bash
#
# Password Management Servlets (PWM)
# http://code.google.com/p/pwm/
#
# Copyright (c) 2011 The PWM Project
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
#

function usage() {
  echo "This script can be used on a modern unix like environment in order to"
  echo "determine the missing strings in localisation property files."
  echo
  echo "Usage:"
  echo "  $0 <path to tomcat>"
  echo 
  echo "<path to tomcat> is the path to the root of your tomcat instance."
  echo
  echo "A file will be created in the current directory for each language"
  echo "/property file combination containing a listing of the variables"
  echo "with a missing translation."
  echo
  echo "Example:"
  echo "  $0 /usr/share/tomcat6"
}


if [ "$1" = "" ]; then
  usage
  exit 1
fi


FILEPATH="${1}/webapps/pwm/WEB-INF/classes/password/pwm/config"

shopt -s nullglob

for i in Display Message PwmError

do
  echo $i
  if [ $i == "PwmError" ]; then
    FILEPATH="${1}/webapps/pwm/WEB-INF/classes/password/pwm/error"
  fi

  for f in $FILEPATH/$i\_* 
  do
    echo Processing: $f

    OUTPUTFILE="Missing_`basename $f`"
	if [ -f "${OUTPUTFILE}" ]; then rm "${OUTPUTFILE}" ; fi

    TMP="Display`date +%s`"
    TFBASE="/tmp/__${TMP}.properties"
    TFLANG="/tmp/__${TMP}_`basename $f`"
    TFMISSING="/tmp/__${TMP}_Missing_`basename $f`"

    grep -v "^[[:space:]]*$" "${FILEPATH}/${i}.properties" | grep -v "^#" | grep "^[[:alpha:]]" | cut -d '=' -f 1 | sort -u > "${TFBASE}"
    grep -v "^[[:space:]]*$" "${f}" | grep -v "^#" | grep "^[[:alpha:]]" | cut -d '=' -f 1 | sort -u > "${TFLANG}"
    diff -w "${TFLANG}" "${TFBASE}" | grep "^>" | cut -d " " -f2- > $TFMISSING

    while read line; do
      grep "$line" "${FILEPATH}/${i}.properties" >> $OUTPUTFILE
    done < "$TFMISSING"


    if [ -f "${TFBASE}" ]; then rm "${TFBASE}" ; fi
    if [ -f "${TFLANG}" ]; then rm "${TFLANG}" ; fi
    if [ -f "${TFMISSING}" ]; then rm "${TFMISSING}" ; fi

  done
done

shopt -u nullglob

exit 0
