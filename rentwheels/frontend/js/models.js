// [RUBRIC: Inheritance] Base class
class Vehicle {
  constructor(o) {
    this.id = o.id; this.name = o.name; this.regNo = o.regNo;
    this.pricePerDay = +o.pricePerDay; this.available = o.available !== false;
    this.type = 'Vehicle';
  }
  calculateRent(days) { return this.pricePerDay * days; }   // overridden by subclasses
  describe() { return ''; }                                  // overridden by subclasses
  static fromObject(o) { return o.type === 'Car' ? new Car(o) : new Bike(o); } // factory
}
// [RUBRIC: Inheritance] Subclass 1 - extends Vehicle, calls super(), overrides methods
class Car extends Vehicle {
  constructor(o) { super(o); this.type = 'Car'; this.seats = +o.seats || 4; this.fuelType = o.fuelType || 'Petrol'; }
  calculateRent(days) { return super.calculateRent(days) + 300; }   // + flat insurance charge
  describe() { return `${this.seats} seats, ${this.fuelType}, insurance Rs 300`; }
}
// [RUBRIC: Inheritance] Subclass 2
class Bike extends Vehicle {
  constructor(o) { super(o); this.type = 'Bike'; this.engineCC = +o.engineCC || 100; }
  calculateRent(days) { return super.calculateRent(days) + 50; }    // + helmet fee
  describe() { return `${this.engineCC} cc, helmet fee Rs 50`; }
}
