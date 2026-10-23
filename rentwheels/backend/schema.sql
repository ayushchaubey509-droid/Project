CREATE DATABASE IF NOT EXISTS rentwheels CHARACTER SET utf8mb4;
USE rentwheels;

CREATE TABLE IF NOT EXISTS users (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) NOT NULL UNIQUE,
  password_hash CHAR(64)     NOT NULL,                 
  role          ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER'
);

CREATE TABLE IF NOT EXISTS vehicles (
  id            INT AUTO_INCREMENT PRIMARY KEY,
  type          ENUM('Car','Bike') NOT NULL,
  name          VARCHAR(100) NOT NULL,
  reg_no        VARCHAR(30)  NOT NULL UNIQUE,
  price_per_day DECIMAL(10,2) NOT NULL CHECK (price_per_day > 0),
  available     BOOLEAN NOT NULL DEFAULT TRUE,
  seats         INT NULL,                             
  fuel_type     VARCHAR(30) NULL,                   
  engine_cc     INT NULL                              
);

CREATE TABLE IF NOT EXISTS bookings (
  id           INT AUTO_INCREMENT PRIMARY KEY,
  user_id      INT NOT NULL,
  vehicle_id   INT NULL,                              
  start_date   DATE NOT NULL,
  end_date     DATE NOT NULL,
  days         INT NOT NULL,
  amount       DECIMAL(10,2) NOT NULL,
  final_amount DECIMAL(10,2) NULL,                     
  late_days    INT NULL,
  status       ENUM('ACTIVE','CANCELLED','RETURNED') NOT NULL DEFAULT 'ACTIVE',
  FOREIGN KEY (user_id)    REFERENCES users(id),
  FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE SET NULL,
  INDEX idx_booking_user (user_id)
);
INSERT IGNORE INTO users (name, email, password_hash, role) VALUES
  ('Admin', 'admin@rent.com', SHA2('admin123', 256), 'ADMIN'),
  ('Riya',  'user@rent.com',  SHA2('user123', 256),  'USER');

INSERT IGNORE INTO vehicles (type, name, reg_no, price_per_day, seats, fuel_type, engine_cc) VALUES
  ('Car',  'Maruti Swift',  'MH-12-1001', 1500, 5, 'Petrol', NULL),
  ('Car',  'Hyundai Creta', 'MH-12-2002', 2500, 5, 'Diesel', NULL),
  ('Car',  'Toyota Innova', 'MH-14-3003', 3200, 7, 'Diesel', NULL),
  ('Bike', 'Honda Activa',  'MH-12-4004', 400,  NULL, NULL, 110),
  ('Bike', 'Bajaj Pulsar',  'MH-12-5005', 600,  NULL, NULL, 150),
  ('Bike', 'Royal Enfield', 'MH-14-6006', 900,  NULL, NULL, 350);
