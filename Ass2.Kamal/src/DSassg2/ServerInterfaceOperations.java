package DSassg2;


/**
* DSassg2/ServerInterfaceOperations.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from ServerInterface.idl
* Monday, July 31, 2017 4:57:43 PM EDT
*/

public interface ServerInterfaceOperations 
{
  String createTRecord (String manager_id, String f_name, String l_name, String addr, String number, String spec, String loc);
  String createSRecord (String manager_id, String f_name, String l_name, String courselist, String status);
  String getRecordCount (String manager_id);
  String display (String manager_id);
  boolean edit (String manager_id, String id, String field_name, String value);
  boolean transferRecord (String manager_id, String record_id, String remoteCenterServerName);
} // interface ServerInterfaceOperations