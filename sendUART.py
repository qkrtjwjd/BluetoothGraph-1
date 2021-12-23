#!/usr/bin/env python
# -*- coding: utf-8 -*-

import serial, time, random

serialsList = []
count = 1
found = False
 
for i in range(64) :
  try :
    port = "COM%d" % i
    ser = serial.Serial(port)
    ser.close()
    if not found:
        print ("Found serials port: ")
    print ("%d. "%count, port)
    found = True
    serialsList.append(port)
  except serial.serialutil.SerialException :
    pass

if not found :
  print ("No found serial ports")
  exit

print ("Type number of port to connect: ")
portNum = int(input())
portString = serialsList.pop(portNum-1)
try :
    ser = serial.Serial(portString)
    ser.baudrate = 9600
    while True :
        temp = random.uniform(2230, 2250)
        msg = "s%d\n"%temp
        ser.write(msg.encode())
        time.sleep(0.5)
except serial.serialutil.SerialException :
    pass
