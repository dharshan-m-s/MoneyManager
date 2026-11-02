# Bundled bank identity assets

Bank marks are stored in `app/src/main/res/drawable-nodpi/` and loaded as local Android
resources. The app does not fetch issuer logos at runtime. The bundled set contains 35 locally verified PNG marks plus the Federal Bank Fortuna Wave
vector insignia for the institutions represented in the app's bank catalogue.

The bundled PNG files are sourced by IFSC slug from
[praveenpuglia/indian-banks](https://github.com/praveenpuglia/indian-banks). Its README
describes the bank-name/logo collection as sourced from official listings. The upstream
README identifies each logo as `assets/logos/<slug>/logo.png`; the corresponding source
slugs used here are:

| Bank | IFSC slug | Local resource |
| --- | --- | --- |
| Axis Bank | `utib` | `bank_logo_axis.png` |
| AU Small Finance Bank | `aubl` | `bank_logo_au_small_finance.png` |
| Bandhan Bank | `bdbl` | `bank_logo_bandhan.png` |
| Bank of Baroda | `barb` | `bank_logo_bank_of_baroda.png` |
| Bank of India | `bkid` | `bank_logo_bank_of_india.png` |
| Bank of Maharashtra | `mahb` | `bank_logo_bank_of_maharashtra.png` |
| Canara Bank | `cnrb` | `bank_logo_canara.png` |
| Central Bank of India | `cbin` | `bank_logo_central_bank.png` |
| City Union Bank | `ciub` | `bank_logo_city_union.png` |
| DBS Bank India | `dbss` | `bank_logo_dbs.png` |
| DCB Bank | `dcbl` | `bank_logo_dcb.png` |
| Deutsche Bank | `deut` | `bank_logo_deutsche.png` |
| Dhanlaxmi Bank | `dlxb` | `bank_logo_dhanlaxmi.png` |
| Equitas Small Finance Bank | `esfb` | `bank_logo_equitas.png` |
| Federal Bank | `fdrl` | `bank_logo_federal.xml` |
| HDFC Bank | `hdfc` | `bank_logo_hdfc.png` |
| ICICI Bank | `icic` | `bank_logo_icici.png` |
| IDBI Bank | `ibkl` | `bank_logo_idbi.png` |
| IDFC FIRST Bank | `idfb` | `bank_logo_idfc_first.png` |
| Indian Bank | `idib` | `bank_logo_indian_bank.png` |
| Indian Overseas Bank | `ioba` | `bank_logo_indian_overseas.png` |
| IndusInd Bank | `indb` | `bank_logo_indusind.png` |
| Jammu & Kashmir Bank | `jaka` | `bank_logo_jk_bank.png` |
| Karnataka Bank | `karb` | `bank_logo_karnataka.png` |
| Karur Vysya Bank | `kvbl` | `bank_logo_karur_vysya.png` |
| Kotak Mahindra Bank | `kkbk` | `bank_logo_kotak.png` |
| Nainital Bank | `ntbl` | `bank_logo_nainital.png` |
| Punjab & Sind Bank | `psib` | `bank_logo_punjab_sind.png` |
| Punjab National Bank | `punb` | `bank_logo_pnb.png` |
| RBL Bank | `ratn` | `bank_logo_rbl.png` |
| South Indian Bank | `sibl` | `bank_logo_south_indian.png` |
| State Bank of India | `sbin` | `bank_logo_sbi.png` |
| Tamilnad Mercantile Bank | `tmbl` | `bank_logo_tamilnad_mercantile.png` |
| UCO Bank | `ucba` | `bank_logo_uco.png` |
| Union Bank of India | `ubin` | `bank_logo_union.png` |
| YES Bank | `yesb` | `bank_logo_yes.png` |

Federal Bank introduced its Fortuna Wave insignia on 6 January 2026. The bundled Federal Bank
vector uses the bank's official blue insignia form, sourced from its published brand identity
announcement: https://www.federal.bank.in/federal-bank-unveils-the-fortuna-wave . The official
press kit is available at https://www.federal.bank.in/federal-bank-official-logo-kit?tab=2 .
The three waves represent authenticity, togetherness and prosperity.

Legacy catalogue names use successor marks where the bank identity is established: Andhra Bank
and Corporation Bank use Union Bank; Oriental Bank of Commerce and United Bank use Punjab National
Bank; Syndicate Bank uses Canara Bank; Vijaya Bank uses Bank of Baroda; ING Vysya Bank uses Kotak
Mahindra Bank; and Lakshmi Vilas Bank uses DBS.

Two institutions in the picker without a verified local logo asset—Akola Urban Co-operative
Bank and The Nawanagar Co-operative Bank—retain a neutral initials fallback.
It is not presented as an official logo. Bank names and marks are their respective owners'
trademarks; the app uses them only to identify the institution and does not imply endorsement
or affiliation.
