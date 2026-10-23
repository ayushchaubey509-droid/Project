package rentwheels;

public class Bike extends Vehicle {
    public int engineCC = 100;
    public Bike() { type = "Bike"; }
    @Override public double calculateRent(int days) { return baseRent(days) + 50; }   
}
