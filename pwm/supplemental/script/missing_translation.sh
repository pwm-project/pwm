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
  echo "The script must be run from the folder where the property files"
  echo "reside."
  echo
  echo "Usage:"
  echo "  $0 <category> <locale>"
  echo 
  echo "<category> can be 'Message', 'Display' or 'Error'."
  echo "<language> can be the code of any of the supported locales"
  echo "The propert3syies files must be in your working directory."
  echo
  echo "Example:"
  echo "  $0 Message it"
}

CAT=$1
LCODE=$2

if [ "$1" = "" -o "$2" = "" ]; then
  usage
  exit 1
fi

if [ ! -f "${CAT}.properties" ]; then
  echo "Base properties file for category ${CAT} not found"
  exit 1
fi

if [ ! -f "${CAT}_${LCODE}.properties" ]; then
  echo "Translated properties file for category ${CAT}, language ${LCODE} not found"
  exit 1
fi

TMP="${CAT}`date +%s`"
TFBASE="/tmp/__${TMP}.properties"
TFLANG="/tmp/__${TMP}_${LCODE}.properties"

grep -v "^[[:space:]]*$" "${CAT}.properties" | grep -v "^#" | grep "^[[:alpha:]]" | cut -d '=' -f 1 | sort -u > "${TFBASE}"
grep -v "^[[:space:]]*$" "${CAT}_${LCODE}.properties" | grep -v "^#" | grep "^[[:alpha:]]" | cut -d '=' -f 1 | sort -u > "${TFLANG}"
diff -w "${TFLANG}" "${TFBASE}" | grep "^>" | cut -d " " -f2-

if [ -f "${TFBASE}" ]; then rm "${TFBASE}" ; fi
if [ -f "${TFLANG}" ]; then rm "${TFLANG}" ; fi
exit 0
