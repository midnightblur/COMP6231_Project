package HelloApp;


/**
* HelloApp/HelloOperations.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from C:/Users/kenlo/Documents/IntelliJ/A2Corba/src/Hello.idl
* Sunday, July 9, 2017 5:28:47 o'clock PM EDT
*/

public interface HelloOperations 
{
  String createTRecord (String managerID, String firstName, String lastName, String address, String phone, String[] specialization, String location);
  String createSRecord (String managerID, String firstName, String lastName, String[] courses, String status, String statusDate);
  String getRecordCounts (String managerID);
  String editRecord (String managerID, String recordID, String fieldName, String newValue);
  String transferRecord (String managerID, String recordID, String remoteCenterServerName);
  void shutdown ();
} // interface HelloOperations