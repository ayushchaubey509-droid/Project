// [RUBRIC: Exception Handling] Custom exception hierarchy: all extend AppError (which extends Error)
class AppError extends Error { constructor(msg){ super(msg); this.name = this.constructor.name; } }
class InvalidDateError extends AppError {}          // bad / past / reversed dates
class VehicleNotAvailableError extends AppError {}  // vehicle already booked
class BookingNotFoundError extends AppError {}      // booking id does not exist
class ValidationError extends AppError {}           // empty / wrong form input
