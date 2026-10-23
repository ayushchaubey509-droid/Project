package rentwheels;

public class Car extends Vehicle {
    public int seats = 4;
    public String fuelType = "Petrol";
    public Car() { type = "Car"; }
    @Override public double calculateRent(int days) { return baseRent(days) + 300; }
}
