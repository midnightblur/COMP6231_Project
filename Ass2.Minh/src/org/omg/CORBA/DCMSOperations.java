package org.omg.CORBA;


/**
* org/omg/CORBA/DCMSOperations.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /Users/quocminhvu/Documents/workspace/IdeaProjects/COMP6231_Assignment02/src/CORBA.idl
* Tuesday, June 27, 2017 3:20:09 o'clock PM EDT
*/

public interface DCMSOperations 
{
  String createTRecord (String managerID, String firstName, String lastName, String address, String phone, String specialization, String location);
  String createSRecord (String managerID, String firstName, String lastName, String coursesRegistered, String status);
  String getRecordCounts (String managerID);
  boolean editRecord (String managerID, String recordID, String fieldName, String newValue);
  boolean transferRecord (String managerID, String recordID, String remoteCenterServerName);
  String getRecordType (String recordID);
  void startUDPServer ();
  String printAllRecords ();
  String printRecord (String managerID, String recordID);
} // interface DCMSOperations
