package org.omg.CORBA;

/**
* org/omg/CORBA/DCMSHolder.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from /Users/quocminhvu/Documents/workspace/IdeaProjects/COMP6231_Project/Ass2.Minh/src/CORBA.idl
* Friday, July 21, 2017 11:13:50 o'clock AM EDT
*/

public final class DCMSHolder implements org.omg.CORBA.portable.Streamable
{
  public org.omg.CORBA.DCMS value = null;

  public DCMSHolder ()
  {
  }

  public DCMSHolder (org.omg.CORBA.DCMS initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = org.omg.CORBA.DCMSHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    org.omg.CORBA.DCMSHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return org.omg.CORBA.DCMSHelper.type ();
  }

}
