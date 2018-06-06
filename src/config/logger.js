const winston = require('winston');

exports.logger = new (winston.Logger)({
  transports: [
    new (winston.transports.Console)({
      level: 'verbose',
      json: true,
      handleExceptions: true,
      stringify: (obj) => JSON.stringify(obj),
   }),
  ],
  exitOnError: false,
});
