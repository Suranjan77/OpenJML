// This example had a problem with crashing, because of the lack of helper
// on the functions used in the invariant.
public class Add
{
  //@ public invariant x() + y() > 0;
  
  private /*@ spec_public */ int my_x;
  private /*@ spec_public */ int my_y;

  //@ requires the_x + the_y > 0;
  //@ ensures x() == the_x && y() == the_y;
  public Add(final int the_x, final int the_y)
  {
    my_x = the_x;
    my_y = the_y;
  }
  
  //@ public normal_behavior ensures \result == my_x;
  public /*@ pure @*/ int x() { return my_x; }
  //@ public normal_behavior ensures \result == my_y;
  public /*@ pure @*/ int y() { return my_y; }
  
  //@ public normal_behavior ensures \result == x() + y() + the_operand;
  public /*@ pure @*/ int sum(final int the_operand)
  {
    return my_x + my_y + the_operand;
  }
}
