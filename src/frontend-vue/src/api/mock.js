// src/api/TradeDashboard.js
const mock = {
  comprehensive: {
    corpusEntryCount: 1234567,
    dataEntryCount: 9876543,
    queryVisitCount: 4567890,
    tradeCountryCount: 45
  },
  countryRatio: [
    { country: '哈萨克斯坦', amount: 420000 },
    { country: '乌兹别克斯坦', amount: 380000 },
    { country: '吉尔吉斯斯坦', amount: 220000 },
    { country: '土库曼斯坦', amount: 180000 },
    { country: '塔吉克斯坦', amount: 120000 }
  ],
  methodRatio: [
    { method: '一般贸易', value: 320000 },
    { method: '边境贸易', value: 280000 },
    { method: '加工贸易', value: 150000 },
    { method: '其他贸易', value: 100000 }
  ],
  last12Months: [
    { month: '2024-01', importQty: 120, exportQty: 80, importAmt: 2400, exportAmt: 1600 },
    { month: '2024-02', importQty: 110, exportQty: 85, importAmt: 2200, exportAmt: 1700 },
    { month: '2024-03', importQty: 130, exportQty: 90, importAmt: 2600, exportAmt: 1800 },
    { month: '2024-04', importQty: 125, exportQty: 95, importAmt: 2500, exportAmt: 1900 },
    { month: '2024-05', importQty: 140, exportQty: 100, importAmt: 2800, exportAmt: 2000 },
    { month: '2024-06', importQty: 135, exportQty: 105, importAmt: 2700, exportAmt: 2100 },
    { month: '2024-07', importQty: 150, exportQty: 110, importAmt: 3000, exportAmt: 2200 },
    { month: '2024-08', importQty: 145, exportQty: 115, importAmt: 2900, exportAmt: 2300 },
    { month: '2024-09', importQty: 160, exportQty: 120, importAmt: 3200, exportAmt: 2400 },
    { month: '2024-10', importQty: 155, exportQty: 125, importAmt: 3100, exportAmt: 2500 },
    { month: '2024-11', importQty: 170, exportQty: 130, importAmt: 3400, exportAmt: 2600 },
    { month: '2024-12', importQty: 165, exportQty: 135, importAmt: 3300, exportAmt: 2700 }
  ],
  yearlyCorpus: [
    { year: 2020, count: 120000 },
    { year: 2021, count: 180000 },
    { year: 2022, count: 250000 },
    { year: 2023, count: 320000 },
    { year: 2024, count: 380000 }
  ]
}

export const getCurrentComprehensive = () => Promise.resolve(mock.comprehensive)
export const getCountryRatio = () => Promise.resolve(mock.countryRatio)
export const getMethodRatio = () => Promise.resolve(mock.methodRatio)
export const getLast12Months = () => Promise.resolve(mock.last12Months)
export const getYearlyCorpus = () => Promise.resolve(mock.yearlyCorpus)