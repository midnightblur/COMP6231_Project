package DSassg2;


/**
* DSassg2/coursesHolder.java .
* Generated by the IDL-to-Java compiler (portable), version "3.2"
* from ServerInterface.idl
* Monday, July 31, 2017 4:57:43 PM EDT
*/

public final class coursesHolder implements org.omg.CORBA.portable.Streamable
{
  public String value[] = null;

  public coursesHolder ()
  {
  }

  public coursesHolder (String[] initialValue)
  {
    value = initialValue;
  }

  public void _read (org.omg.CORBA.portable.InputStream i)
  {
    value = DSassg2.coursesHelper.read (i);
  }

  public void _write (org.omg.CORBA.portable.OutputStream o)
  {
    DSassg2.coursesHelper.write (o, value);
  }

  public org.omg.CORBA.TypeCode _type ()
  {
    return DSassg2.coursesHelper.type ();
  }

}