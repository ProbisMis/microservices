-- Create order-service database
CREATE DATABASE IF NOT EXISTS `order-service`;

-- Create inventory-service database
CREATE DATABASE IF NOT EXISTS `inventory-service`;

-- Optional: Ensure permissions are correct for your user
GRANT ALL PRIVILEGES ON `order-service`.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON `inventory-service`.* TO 'root'@'%';