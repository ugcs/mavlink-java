#!/bin/sh

CLASSPATH=.:*:lib/*
shift
java -cp ${CLASSPATH} com.ugcs.mavlink.generator.Mavlink2Java $
