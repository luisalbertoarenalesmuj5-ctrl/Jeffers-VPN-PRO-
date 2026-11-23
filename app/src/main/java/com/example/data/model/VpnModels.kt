package com.example.data.model

enum class CarrierType(
    val displayName: String,
    val defaultPort: Int,
    val defaultSniHost: String,
    val accentColorHex: Long
) {
    TIGO(displayName = "Tigo", defaultPort = 8080, defaultSniHost = "m.tigo.com", accentColorHex = 0xFF00A3FF),
    CLARO(displayName = "Claro", defaultPort = 80, defaultSniHost = "www.claro.com", accentColorHex = 0xFFE8455A),
    MOVISTAR(displayName = "Movistar", defaultPort = 8080, defaultSniHost = "wap.movistar.com", accentColorHex = 0xFF019DF4),
    TELCEL(displayName = "Telcel", defaultPort = 8080, defaultSniHost = "www.telcel.com", accentColorHex = 0xFF0072CE),
    ATT(displayName = "AT&T", defaultPort = 8080, defaultSniHost = "www.att.com.mx", accentColorHex = 0xFF00A8E0),
    BAIT(displayName = "Bait", defaultPort = 80, defaultSniHost = "mibait.com", accentColorHex = 0xFFFACC15),
    UNEFON(displayName = "Unefon", defaultPort = 80, defaultSniHost = "www.unefon.com.mx", accentColorHex = 0xFF38BDF8),
    OXXO_CEL(displayName = "OXXO Cel", defaultPort = 80, defaultSniHost = "www.oxxocel.com", accentColorHex = 0xFFEF4444),
    PILLOFON(displayName = "Pillofón", defaultPort = 8080, defaultSniHost = "www.pillofon.mx", accentColorHex = 0xFF22D3EE),
    DIRI(displayName = "Diri", defaultPort = 80, defaultSniHost = "www.diri.mx", accentColorHex = 0xFF6366F1),
    FREEDOMPOP(displayName = "FreedomPop", defaultPort = 8080, defaultSniHost = "www.freedompop.mx", accentColorHex = 0xFF0EA5E9),
    TELMEX(displayName = "Telmex", defaultPort = 80, defaultSniHost = "www.telmex.com", accentColorHex = 0xFF0284C7),
    TOTALPLAY(displayName = "Totalplay", defaultPort = 8080, defaultSniHost = "www.totalplay.com.mx", accentColorHex = 0xFF8B5CF6),
    IZZI(displayName = "Izzi", defaultPort = 80, defaultSniHost = "www.izzi.mx", accentColorHex = 0xFFF97316),
    MEGACABLE(displayName = "Megacable", defaultPort = 8080, defaultSniHost = "www.megacable.com.mx", accentColorHex = 0xFF14B8A6),
    PERSONAL(displayName = "Personal", defaultPort = 8080, defaultSniHost = "wap.personal.com.ar", accentColorHex = 0xFF00B4D8),
    IMOWI(displayName = "Imowi", defaultPort = 8080, defaultSniHost = "www.imowi.com.ar", accentColorHex = 0xFF22C55E),
    TELECENTRO(displayName = "Telecentro", defaultPort = 80, defaultSniHost = "www.telecentro.com.ar", accentColorHex = 0xFF2563EB),
    ENTEL(displayName = "Entel", defaultPort = 8080, defaultSniHost = "mi.entel.pe", accentColorHex = 0xFF3B82F6),
    BITEL(displayName = "Bitel", defaultPort = 80, defaultSniHost = "wap.bitel.com.pe", accentColorHex = 0xFFFACC15),
    WIN(displayName = "WIN", defaultPort = 80, defaultSniHost = "www.win.pe", accentColorHex = 0xFFF97316),
    DIGICEL(displayName = "Digicel", defaultPort = 8080, defaultSniHost = "m.digicelgroup.com", accentColorHex = 0xFFEF4444),
    DIGITEL(displayName = "Digitel", defaultPort = 8080, defaultSniHost = "www.digitel.com.ve", accentColorHex = 0xFF38BDF8),
    WOM(displayName = "WOM", defaultPort = 8080, defaultSniHost = "www.wom.co", accentColorHex = 0xFFA855F7),
    VIRGIN(displayName = "Virgin Mobile", defaultPort = 8080, defaultSniHost = "www.virginmobile.co", accentColorHex = 0xFFE11D48),
    ETB(displayName = "ETB", defaultPort = 8080, defaultSniHost = "www.etb.com", accentColorHex = 0xFF0284C7),
    MOVIL_EXITO(displayName = "Móvil Éxito", defaultPort = 80, defaultSniHost = "www.movilexito.com", accentColorHex = 0xFFFACC15),
    KALLEY_MOVIL(displayName = "Kalley Móvil", defaultPort = 8080, defaultSniHost = "www.kalleymovil.com.co", accentColorHex = 0xFFEF4444),
    FLASH_MOBILE(displayName = "Flash Mobile", defaultPort = 80, defaultSniHost = "www.flashmobile.co", accentColorHex = 0xFF10B981),
    TUENTI(displayName = "Tuenti", defaultPort = 80, defaultSniHost = "www.tuenti.ec", accentColorHex = 0xFFEC4899),
    CNT(displayName = "CNT", defaultPort = 8080, defaultSniHost = "www.cnt.gob.ec", accentColorHex = 0xFF0EA5E9),
    NETLIFE(displayName = "Netlife", defaultPort = 8080, defaultSniHost = "www.netlife.ec", accentColorHex = 0xFFF97316),
    GUATEL(displayName = "Guatel", defaultPort = 8080, defaultSniHost = "www.guatel.gob.gt", accentColorHex = 0xFF38BDF8),
    CABLE_COLOR(displayName = "Cable Color", defaultPort = 8080, defaultSniHost = "www.cablecolor.com.gt", accentColorHex = 0xFFEC4899),
    MULTINET(displayName = "Multinet", defaultPort = 80, defaultSniHost = "www.serviciosmultinet.com", accentColorHex = 0xFF22C55E),
    STARLINK(displayName = "Starlink", defaultPort = 8080, defaultSniHost = "www.starlink.com", accentColorHex = 0xFF94A3B8),
    MAS_MOVIL(displayName = "Más Móvil", defaultPort = 8080, defaultSniHost = "www.masmovilpanama.com", accentColorHex = 0xFFFB923C),
    MOVILNET(displayName = "Movilnet", defaultPort = 8080, defaultSniHost = "www.movilnet.com.ve", accentColorHex = 0xFFF97316),
    CANTV(displayName = "CANTV", defaultPort = 80, defaultSniHost = "www.cantv.com.ve", accentColorHex = 0xFFEF4444),
    INTER(displayName = "Inter", defaultPort = 8080, defaultSniHost = "www.inter.com.ve", accentColorHex = 0xFF10B981),
    NETUNO(displayName = "NetUno", defaultPort = 80, defaultSniHost = "www.netuno.net", accentColorHex = 0xFF0EA5E9),
    CUBACEL(displayName = "Cubacel", defaultPort = 80, defaultSniHost = "nauta.cu", accentColorHex = 0xFF22C55E),
    ETECSA_NAUTA(displayName = "ETECSA Nauta", defaultPort = 8080, defaultSniHost = "www.etecsa.cu", accentColorHex = 0xFF0EA5E9),
    NAUTA_HOGAR(displayName = "Nauta Hogar", defaultPort = 80, defaultSniHost = "www.nauta.cu", accentColorHex = 0xFF10B981),
    WIFI_ETECSA(displayName = "Wi-Fi ETECSA", defaultPort = 8080, defaultSniHost = "www.etecsa.cu", accentColorHex = 0xFF38BDF8),
    HONDUTEL(displayName = "Hondutel", defaultPort = 80, defaultSniHost = "www.hondutel.hn", accentColorHex = 0xFF14B8A6),
    COOTEL(displayName = "Cootel", defaultPort = 80, defaultSniHost = "www.cootel.com.ni", accentColorHex = 0xFF38BDF8),
    IBW(displayName = "IBW", defaultPort = 8080, defaultSniHost = "www.ibw.com.ni", accentColorHex = 0xFF2563EB),
    YOTA(displayName = "Yota", defaultPort = 8080, defaultSniHost = "www.yota.com.ni", accentColorHex = 0xFF06B6D4),
    VTR(displayName = "VTR", defaultPort = 8080, defaultSniHost = "www.vtr.com", accentColorHex = 0xFF8B5CF6),
    MUNDO(displayName = "Mundo", defaultPort = 80, defaultSniHost = "www.tumundo.cl", accentColorHex = 0xFF22C55E),
    GTD(displayName = "GTD Móvil", defaultPort = 8080, defaultSniHost = "www.gtd.cl", accentColorHex = 0xFF0EA5E9),
    VIVA(displayName = "Viva", defaultPort = 8080, defaultSniHost = "www.viva.com.bo", accentColorHex = 0xFF84CC16),
    COTAS(displayName = "Cotas", defaultPort = 80, defaultSniHost = "www.cotas.com", accentColorHex = 0xFF0284C7),
    AXS(displayName = "AXS Bolivia", defaultPort = 8080, defaultSniHost = "www.axsbolivia.com", accentColorHex = 0xFFF97316),
    ANTEL(displayName = "Antel", defaultPort = 8080, defaultSniHost = "www.antel.com.uy", accentColorHex = 0xFF2563EB),
    DEDICADO(displayName = "Dedicado", defaultPort = 8080, defaultSniHost = "www.dedicado.com.uy", accentColorHex = 0xFF0EA5E9),
    VOX(displayName = "Vox", defaultPort = 80, defaultSniHost = "www.vox.com.py", accentColorHex = 0xFFEF4444),
    COPACO(displayName = "Copaco", defaultPort = 80, defaultSniHost = "www.copaco.com.py", accentColorHex = 0xFF2563EB),
    ALTICE(displayName = "Altice", defaultPort = 8080, defaultSniHost = "www.altice.com.do", accentColorHex = 0xFF60A5FA),
    WIND(displayName = "Wind Telecom", defaultPort = 80, defaultSniHost = "www.wind.com.do", accentColorHex = 0xFF38BDF8),
    KOLBI(displayName = "Kölbi", defaultPort = 8080, defaultSniHost = "www.kolbi.cr", accentColorHex = 0xFF10B981),
    LIBERTY(displayName = "Liberty", defaultPort = 8080, defaultSniHost = "www.libertycr.com", accentColorHex = 0xFF06B6D4),
    TELECABLE(displayName = "Telecable", defaultPort = 8080, defaultSniHost = "telecablecr.com", accentColorHex = 0xFFEC4899),
    TMOBILE(displayName = "T-Mobile", defaultPort = 8080, defaultSniHost = "www.t-mobile.com", accentColorHex = 0xFFE20074),
    METRO(displayName = "Metro", defaultPort = 8080, defaultSniHost = "www.metrobyt-mobile.com", accentColorHex = 0xFFA855F7),
    CRICKET(displayName = "Cricket", defaultPort = 80, defaultSniHost = "www.cricketwireless.com", accentColorHex = 0xFF22C55E),
    VERIZON(displayName = "Verizon", defaultPort = 8080, defaultSniHost = "www.verizon.com", accentColorHex = 0xFFEF4444),
    BOOST(displayName = "Boost Mobile", defaultPort = 8080, defaultSniHost = "www.boostmobile.com", accentColorHex = 0xFFF97316),
    MINT_MOBILE(displayName = "Mint Mobile", defaultPort = 8080, defaultSniHost = "www.mintmobile.com", accentColorHex = 0xFF10B981),
    VISIBLE(displayName = "Visible", defaultPort = 80, defaultSniHost = "www.visible.com", accentColorHex = 0xFF3B82F6),
    XFINITY(displayName = "Xfinity Mobile", defaultPort = 8080, defaultSniHost = "www.xfinity.com", accentColorHex = 0xFFA855F7),
    AERONET(displayName = "Aeronet", defaultPort = 80, defaultSniHost = "www.aeronetpr.com", accentColorHex = 0xFF0EA5E9),
    VIVO(displayName = "Vivo", defaultPort = 8080, defaultSniHost = "www.vivo.com.br", accentColorHex = 0xFF9333EA),
    TIM(displayName = "TIM", defaultPort = 80, defaultSniHost = "www.tim.com.br", accentColorHex = 0xFF2563EB),
    OI(displayName = "Oi", defaultPort = 80, defaultSniHost = "www.oi.com.br", accentColorHex = 0xFFFACC15),
    ALGAR(displayName = "Algar", defaultPort = 8080, defaultSniHost = "www.algartelecom.com.br", accentColorHex = 0xFF10B981),
    ORANGE(displayName = "Orange", defaultPort = 8080, defaultSniHost = "www.orange.es", accentColorHex = 0xFFF97316),
    VODAFONE(displayName = "Vodafone", defaultPort = 80, defaultSniHost = "www.vodafone.es", accentColorHex = 0xFFDC2626),
    YOIGO(displayName = "Yoigo", defaultPort = 8080, defaultSniHost = "www.yoigo.com", accentColorHex = 0xFFA855F7),
    DIGI(displayName = "Digi", defaultPort = 80, defaultSniHost = "www.digimobil.es", accentColorHex = 0xFF0EA5E9),
    PEPEPHONE(displayName = "Pepephone", defaultPort = 80, defaultSniHost = "www.pepephone.com", accentColorHex = 0xFFEF4444),
    SIMYO(displayName = "Simyo", defaultPort = 8080, defaultSniHost = "www.simyo.es", accentColorHex = 0xFFF97316),
    LOWI(displayName = "Lowi", defaultPort = 80, defaultSniHost = "www.lowi.es", accentColorHex = 0xFFA855F7),
    CUSTOM(displayName = "Personalizado", defaultPort = 8080, defaultSniHost = "vps.jeffersvpn.net", accentColorHex = 0xFFA855F7);

    companion object {
        fun fromName(value: String): CarrierType =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                    it.displayName.equals(value, ignoreCase = true)
            } ?: TIGO
    }
}

enum class TunnelProtocol(val displayName: String, val badge: String) {
    BHTTP(displayName = "BHTTP Directo", badge = "BHTTP"),
    BHTTP_WS(displayName = "BHTTP + WebSocket", badge = "BHTTP-WS"),
    HTTP_CONNECT(displayName = "HTTP CONNECT", badge = "HTTP"),
    SSH_DIRECT(displayName = "SSH Directo (22)", badge = "SSH-22");

    companion object {
        fun fromName(value: String): TunnelProtocol =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BHTTP
    }
}

enum class CountryRegion(
    val displayName: String,
    val titleName: String,
    val flagEmoji: String,
    val isoCode: String,
    val carriers: List<CarrierType>
) {
    ARGENTINA(
        displayName = "ARGENTINA",
        titleName = "Argentina",
        flagEmoji = "\uD83C\uDDE6\uD83C\uDDF7",
        isoCode = "AR",
        carriers = listOf(
            CarrierType.PERSONAL,
            CarrierType.CLARO,
            CarrierType.MOVISTAR,
            CarrierType.TUENTI,
            CarrierType.IMOWI,
            CarrierType.TELECENTRO,
            CarrierType.STARLINK
        )
    ),
    BOLIVIA(
        displayName = "BOLIVIA",
        titleName = "Bolivia",
        flagEmoji = "\uD83C\uDDE7\uD83C\uDDF4",
        isoCode = "BO",
        carriers = listOf(
            CarrierType.ENTEL,
            CarrierType.TIGO,
            CarrierType.VIVA,
            CarrierType.COTAS,
            CarrierType.AXS,
            CarrierType.STARLINK
        )
    ),
    BRASIL(
        displayName = "BRASIL",
        titleName = "Brasil",
        flagEmoji = "\uD83C\uDDE7\uD83C\uDDF7",
        isoCode = "BR",
        carriers = listOf(
            CarrierType.VIVO,
            CarrierType.CLARO,
            CarrierType.TIM,
            CarrierType.OI,
            CarrierType.ALGAR,
            CarrierType.STARLINK
        )
    ),
    CHILE(
        displayName = "CHILE",
        titleName = "Chile",
        flagEmoji = "\uD83C\uDDE8\uD83C\uDDF1",
        isoCode = "CL",
        carriers = listOf(
            CarrierType.ENTEL,
            CarrierType.MOVISTAR,
            CarrierType.CLARO,
            CarrierType.WOM,
            CarrierType.VTR,
            CarrierType.VIRGIN,
            CarrierType.MUNDO,
            CarrierType.GTD,
            CarrierType.STARLINK
        )
    ),
    COLOMBIA(
        displayName = "COLOMBIA",
        titleName = "Colombia",
        flagEmoji = "\uD83C\uDDE8\uD83C\uDDF4",
        isoCode = "CO",
        carriers = listOf(
            CarrierType.CLARO,
            CarrierType.TIGO,
            CarrierType.MOVISTAR,
            CarrierType.WOM,
            CarrierType.VIRGIN,
            CarrierType.ETB,
            CarrierType.MOVIL_EXITO,
            CarrierType.KALLEY_MOVIL,
            CarrierType.FLASH_MOBILE,
            CarrierType.STARLINK
        )
    ),
    COSTA_RICA(
        displayName = "COSTA RICA",
        titleName = "Costa Rica",
        flagEmoji = "\uD83C\uDDE8\uD83C\uDDF7",
        isoCode = "CR",
        carriers = listOf(
            CarrierType.KOLBI,
            CarrierType.LIBERTY,
            CarrierType.CLARO,
            CarrierType.TIGO,
            CarrierType.TELECABLE,
            CarrierType.STARLINK
        )
    ),
    CUBA(
        displayName = "CUBA",
        titleName = "Cuba",
        flagEmoji = "\uD83C\uDDE8\uD83C\uDDFA",
        isoCode = "CU",
        carriers = listOf(
            CarrierType.CUBACEL,
            CarrierType.ETECSA_NAUTA,
            CarrierType.NAUTA_HOGAR,
            CarrierType.WIFI_ETECSA
        )
    ),
    ECUADOR(
        displayName = "ECUADOR",
        titleName = "Ecuador",
        flagEmoji = "\uD83C\uDDEA\uD83C\uDDE8",
        isoCode = "EC",
        carriers = listOf(
            CarrierType.CLARO,
            CarrierType.MOVISTAR,
            CarrierType.TUENTI,
            CarrierType.CNT,
            CarrierType.NETLIFE,
            CarrierType.STARLINK
        )
    ),
    EL_SALVADOR(
        displayName = "EL SALVADOR",
        titleName = "El Salvador",
        flagEmoji = "\uD83C\uDDF8\uD83C\uDDFB",
        isoCode = "SV",
        carriers = listOf(
            CarrierType.TIGO,
            CarrierType.CLARO,
            CarrierType.MOVISTAR,
            CarrierType.DIGICEL,
            CarrierType.CABLE_COLOR,
            CarrierType.STARLINK
        )
    ),
    ESPANA(
        displayName = "ESPAÑA",
        titleName = "España",
        flagEmoji = "\uD83C\uDDEA\uD83C\uDDF8",
        isoCode = "ES",
        carriers = listOf(
            CarrierType.MOVISTAR,
            CarrierType.ORANGE,
            CarrierType.VODAFONE,
            CarrierType.YOIGO,
            CarrierType.DIGI,
            CarrierType.PEPEPHONE,
            CarrierType.SIMYO,
            CarrierType.LOWI
        )
    ),
    ESTADOS_UNIDOS(
        displayName = "ESTADOS UNIDOS",
        titleName = "Estados Unidos",
        flagEmoji = "\uD83C\uDDFA\uD83C\uDDF8",
        isoCode = "US",
        carriers = listOf(
            CarrierType.TMOBILE,
            CarrierType.ATT,
            CarrierType.VERIZON,
            CarrierType.METRO,
            CarrierType.CRICKET,
            CarrierType.BOOST,
            CarrierType.MINT_MOBILE,
            CarrierType.VISIBLE,
            CarrierType.XFINITY,
            CarrierType.STARLINK
        )
    ),
    GENERAL(
        displayName = "GENERAL",
        titleName = "General",
        flagEmoji = "\uD83C\uDF10",
        isoCode = "",
        carriers = listOf(
            CarrierType.TIGO,
            CarrierType.CLARO,
            CarrierType.MOVISTAR,
            CarrierType.TELCEL,
            CarrierType.ATT,
            CarrierType.ENTEL,
            CarrierType.PERSONAL,
            CarrierType.VIVO,
            CarrierType.STARLINK,
            CarrierType.CUSTOM
        )
    ),
    GUATEMALA(
        displayName = "GUATEMALA",
        titleName = "Guatemala",
        flagEmoji = "\uD83C\uDDEC\uD83C\uDDF9",
        isoCode = "GT",
        carriers = listOf(
            CarrierType.TIGO,
            CarrierType.CLARO,
            CarrierType.MOVISTAR,
            CarrierType.TUENTI,
            CarrierType.GUATEL,
            CarrierType.CABLE_COLOR,
            CarrierType.MULTINET,
            CarrierType.STARLINK
        )
    ),
    HONDURAS(
        displayName = "HONDURAS",
        titleName = "Honduras",
        flagEmoji = "\uD83C\uDDED\uD83C\uDDF3",
        isoCode = "HN",
        carriers = listOf(
            CarrierType.TIGO,
            CarrierType.CLARO,
            CarrierType.HONDUTEL,
            CarrierType.CABLE_COLOR,
            CarrierType.MOVISTAR,
            CarrierType.STARLINK
        )
    ),
    MEXICO(
        displayName = "MEXICO",
        titleName = "México",
        flagEmoji = "\uD83C\uDDF2\uD83C\uDDFD",
        isoCode = "MX",
        carriers = listOf(
            CarrierType.TELCEL,
            CarrierType.ATT,
            CarrierType.MOVISTAR,
            CarrierType.BAIT,
            CarrierType.UNEFON,
            CarrierType.VIRGIN,
            CarrierType.OXXO_CEL,
            CarrierType.PILLOFON,
            CarrierType.DIRI,
            CarrierType.FREEDOMPOP,
            CarrierType.TELMEX,
            CarrierType.TOTALPLAY,
            CarrierType.IZZI,
            CarrierType.MEGACABLE,
            CarrierType.STARLINK
        )
    ),
    NICARAGUA(
        displayName = "NICARAGUA",
        titleName = "Nicaragua",
        flagEmoji = "\uD83C\uDDF3\uD83C\uDDEE",
        isoCode = "NI",
        carriers = listOf(
            CarrierType.TIGO,
            CarrierType.CLARO,
            CarrierType.COOTEL,
            CarrierType.IBW,
            CarrierType.YOTA,
            CarrierType.MOVISTAR,
            CarrierType.STARLINK
        )
    ),
    PANAMA(
        displayName = "PANAMA",
        titleName = "Panamá",
        flagEmoji = "\uD83C\uDDF5\uD83C\uDDE6",
        isoCode = "PA",
        carriers = listOf(
            CarrierType.TIGO,
            CarrierType.MAS_MOVIL,
            CarrierType.CLARO,
            CarrierType.DIGICEL,
            CarrierType.MOVISTAR,
            CarrierType.STARLINK
        )
    ),
    PARAGUAY(
        displayName = "PARAGUAY",
        titleName = "Paraguay",
        flagEmoji = "\uD83C\uDDF5\uD83C\uDDFE",
        isoCode = "PY",
        carriers = listOf(
            CarrierType.TIGO,
            CarrierType.PERSONAL,
            CarrierType.CLARO,
            CarrierType.VOX,
            CarrierType.COPACO,
            CarrierType.STARLINK
        )
    ),
    PERU(
        displayName = "PERU",
        titleName = "Perú",
        flagEmoji = "\uD83C\uDDF5\uD83C\uDDEA",
        isoCode = "PE",
        carriers = listOf(
            CarrierType.CLARO,
            CarrierType.MOVISTAR,
            CarrierType.ENTEL,
            CarrierType.BITEL,
            CarrierType.WIN,
            CarrierType.STARLINK
        )
    ),
    PUERTO_RICO(
        displayName = "PUERTO RICO",
        titleName = "Puerto Rico",
        flagEmoji = "\uD83C\uDDF5\uD83C\uDDF7",
        isoCode = "PR",
        carriers = listOf(
            CarrierType.CLARO,
            CarrierType.TMOBILE,
            CarrierType.LIBERTY,
            CarrierType.BOOST,
            CarrierType.AERONET,
            CarrierType.STARLINK
        )
    ),
    REP_DOMINICANA(
        displayName = "REP. DOMINICANA",
        titleName = "Rep. Dominicana",
        flagEmoji = "\uD83C\uDDE9\uD83C\uDDF4",
        isoCode = "DO",
        carriers = listOf(
            CarrierType.CLARO,
            CarrierType.ALTICE,
            CarrierType.VIVA,
            CarrierType.WIND,
            CarrierType.STARLINK
        )
    ),
    URUGUAY(
        displayName = "URUGUAY",
        titleName = "Uruguay",
        flagEmoji = "\uD83C\uDDFA\uD83C\uDDFE",
        isoCode = "UY",
        carriers = listOf(
            CarrierType.ANTEL,
            CarrierType.MOVISTAR,
            CarrierType.CLARO,
            CarrierType.DEDICADO,
            CarrierType.STARLINK
        )
    ),
    VENEZUELA(
        displayName = "VENEZUELA",
        titleName = "Venezuela",
        flagEmoji = "\uD83C\uDDFB\uD83C\uDDEA",
        isoCode = "VE",
        carriers = listOf(
            CarrierType.MOVISTAR,
            CarrierType.DIGITEL,
            CarrierType.MOVILNET,
            CarrierType.CANTV,
            CarrierType.INTER,
            CarrierType.NETUNO
        )
    );

    fun regionalSniFor(carrier: CarrierType): String {
        val cc = isoCode.lowercase()
        if (cc.isEmpty()) return carrier.defaultSniHost
        return when (carrier) {
            CarrierType.CLARO -> when (this) {
                CHILE -> "www.miclaro.cl"
                BRASIL -> "www.claro.com.br"
                COLOMBIA, GUATEMALA, EL_SALVADOR, HONDURAS, NICARAGUA, PANAMA, PERU, ECUADOR, ARGENTINA, PARAGUAY, URUGUAY, COSTA_RICA, REP_DOMINICANA, PUERTO_RICO ->
                    "www.claro.com.$cc"
                else -> carrier.defaultSniHost
            }
            CarrierType.TIGO -> when (this) {
                COLOMBIA, PANAMA, BOLIVIA, PARAGUAY -> "www.tigo.com.$cc"
                COSTA_RICA -> "www.tigo.cr"
                else -> "m.tigo.com.$cc"
            }
            CarrierType.MOVISTAR -> when (this) {
                ESPANA -> "www.movistar.es"
                CHILE -> "www.movistar.cl"
                MEXICO -> "www.movistar.com.mx"
                GUATEMALA -> "www.movistar.com.gt"
                else -> "wap.movistar.com.$cc"
            }
            CarrierType.TUENTI -> when (this) {
                ARGENTINA -> "www.tuenti.com.ar"
                GUATEMALA -> "www.tuenti.gt"
                else -> "www.tuenti.ec"
            }
            CarrierType.CABLE_COLOR -> when (this) {
                HONDURAS -> "www.cablecolor.hn"
                EL_SALVADOR -> "www.cablecolor.com.sv"
                else -> "www.cablecolor.com.gt"
            }
            CarrierType.ENTEL -> if (this == CHILE) "www.entel.cl" else if (this == BOLIVIA) "www.entel.bo" else "mi.entel.pe"
            CarrierType.PERSONAL -> if (this == PARAGUAY) "www.personal.com.py" else "wap.personal.com.ar"
            CarrierType.WOM -> if (this == CHILE) "www.wom.cl" else "www.wom.co"
            CarrierType.VIRGIN -> when (this) {
                MEXICO -> "www.virginmobile.mx"
                CHILE -> "www.virginmobile.cl"
                else -> "www.virginmobile.co"
            }
            CarrierType.VIVA -> if (this == REP_DOMINICANA) "www.viva.com.do" else "www.viva.com.bo"
            CarrierType.LIBERTY -> if (this == PUERTO_RICO) "www.libertypr.com" else "www.libertycr.com"
            CarrierType.ATT -> if (this == ESTADOS_UNIDOS) "www.att.com" else "www.att.com.mx"
            else -> carrier.defaultSniHost
        }
    }

    fun carriersForPlan(tier: PlanTier): List<CarrierType> {
        val targetCount = tier.companyQuotaPerCountry
        if (carriers.isEmpty()) return emptyList()
        if (tier == PlanTier.FREE) {
            return List(targetCount) { idx -> carriers[idx % carriers.size] }
        }
        val startOffset = when (tier) {
            PlanTier.FREE -> 0
            PlanTier.PRO -> 4
            PlanTier.VIP -> 9
            PlanTier.PREMIUM -> 14
            PlanTier.SUPER_PRO -> 19
        }
        return if (carriers.size >= targetCount) {
            List(targetCount) { i -> carriers[(startOffset + i) % carriers.size] }
        } else {
            List(targetCount) { i -> carriers[(startOffset + i) % carriers.size] }
        }
    }

    companion object {
        fun fromName(value: String): CountryRegion =
            entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                    it.displayName.equals(value, ignoreCase = true) ||
                    it.titleName.equals(value, ignoreCase = true)
            } ?: GENERAL

        fun fromIsoCode(iso: String?): CountryRegion {
            val clean = iso?.trim()?.uppercase().orEmpty()
            if (clean.isEmpty()) return GUATEMALA
            return entries.firstOrNull { it.isoCode.isNotEmpty() && it.isoCode == clean } ?: GENERAL
        }
    }
}

enum class VpnPerformanceMode(
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
    val mtu: Int,
    val keepAliveMs: Long
) {
    BALANCED(
        title = "Equilibrado",
        subtitle = "Uso general — navegación, redes sociales, streaming",
        bullets = listOf("Estabilidad máxima", "Menor consumo de batería", "DNS optimizado"),
        mtu = 1400,
        keepAliveMs = 15000L
    ),
    GAMING(
        title = "Modo Juego",
        subtitle = "Baja latencia y respuesta rápida en paquetes UDP/TCP",
        bullets = listOf("Prioridad de paquetes", "Menor jitter en túnel", "Keep-alive rápido"),
        mtu = 1360,
        keepAliveMs = 5000L
    ),
    POWER_SAVER(
        title = "Ahorro de energía",
        subtitle = "Reduce CPU, radio y RAM sin cortar la conexión",
        bullets = listOf("Menor uso de batería", "Intervalo de latido extendido", "Ideal en reposo"),
        mtu = 1400,
        keepAliveMs = 30000L
    ),
    MAX_PERFORMANCE(
        title = "Máximo rendimiento",
        subtitle = "Usa todos los recursos disponibles para descargas rápidas",
        bullets = listOf("MTU 1500 completo", "Sin límites de búfer", "Reconectividad inmediata"),
        mtu = 1500,
        keepAliveMs = 4000L
    );

    companion object {
        fun fromName(value: String): VpnPerformanceMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BALANCED
    }
}

enum class BackgroundEffect(val label: String, val subtitle: String) {
    CHRISTMAS_SNOW("Nieve Navideña", "Copos invernales y destellos"),
    SOLID_BLACK("Negro sólido", "Sin animación"),
    TECH_GRID("Tech Grid", "Grid animado + escaneo"),
    IMAGE("Imagen", "Fondo cibernético"),
    PARTICLES("Partículas", "Colores dinámicos"),
    GALAXY("Galaxia", "Estrellas realistas"),
    NEO_TEK("Neo Tek", "Neón tecnológico");

    companion object {
        fun fromName(value: String): BackgroundEffect =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CHRISTMAS_SNOW
    }
}

enum class ServerAnimationStyle(val label: String) {
    BITS("Bits"),
    CYBER("Cibernético"),
    NETWORK("Red"),
    LIGHTNING("Rayos"),
    GALAXY("Galaxia"),
    PARTICLES("Partículas");

    companion object {
        fun fromName(value: String): ServerAnimationStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BITS
    }
}

enum class ThemePreset(
    val title: String,
    val primaryHex: Long,
    val accentHex: Long,
    val backgroundHex: Long,
    val surfaceHex: Long,
    val errorHex: Long
) {
    CHRISTMAS_LUXURY(
        title = "Navidad Dorada",
        primaryHex = 0xFFF4C44E,
        accentHex = 0xFF2ECC71,
        backgroundHex = 0xFF030B08,
        surfaceHex = 0xFF091B14,
        errorHex = 0xFFE63946
    ),
    NOEL_RUBY(
        title = "Noel Carmesí",
        primaryHex = 0xFFE63946,
        accentHex = 0xFFF4C44E,
        backgroundHex = 0xFF090408,
        surfaceHex = 0xFF170D14,
        errorHex = 0xFFFF5A6E
    ),
    JEFFERS_ORIGINAL(
        title = "JEFFERS Original",
        primaryHex = 0xFF00D4FF,
        accentHex = 0xFF39D353,
        backgroundHex = 0xFF000000,
        surfaceHex = 0xFF10151D,
        errorHex = 0xFFE8455A
    ),
    DARK_PURPLE(
        title = "Púrpura Oscuro",
        primaryHex = 0xFFA855F7,
        accentHex = 0xFFC084FC,
        backgroundHex = 0xFF06040C,
        surfaceHex = 0xFF161124,
        errorHex = 0xFFF43F5E
    ),
    MATRIX_GREEN(
        title = "Verde Matrix",
        primaryHex = 0xFF22C55E,
        accentHex = 0xFF4ADE80,
        backgroundHex = 0xFF020804,
        surfaceHex = 0xFF0D1B12,
        errorHex = 0xFFEF4444
    ),
    FIRE_ORANGE(
        title = "Naranja Fuego",
        primaryHex = 0xFFF97316,
        accentHex = 0xFFFACC15,
        backgroundHex = 0xFF0A0502,
        surfaceHex = 0xFF1D130D,
        errorHex = 0xFFEF4444
    );

    val isChristmas: Boolean
        get() = this == CHRISTMAS_LUXURY || this == NOEL_RUBY

    companion object {
        fun fromName(value: String): ThemePreset =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CHRISTMAS_LUXURY
    }
}

enum class ChristmasSoundStyle(val title: String, val subtitle: String) {
    CELESTA_LUXURY("Jingle Bells Pro", "Celesta dorada, arpa y cascabeles"),
    SLEIGH_BELLS("Feliz Navidad Bell", "Campanas festivas «We Wish You...»"),
    CAROL_OF_THE_BELLS("Campanas de Belén", "Carillón épico de Navidad y bajos profundos"),
    DECK_THE_HALLS("Alegría Navideña", "Villancico «Deck the Halls» y cascabeles"),
    ADESTE_FIDELES("Venid Fieles Todos", "Coro navideño dorado y campanas de catedral"),
    RUDOLPH_BELLS("Rodolfo el Reno", "Ritmo alegre de trineo con cascabeles dobles"),
    WINTER_WONDERLAND("Navidad Blanca Pro", "Vibráfono invernal cálido y arpegios de nieve"),
    MUSIC_BOX("Noche de Paz Box", "Caja musical clásica y arpa invernal");

    companion object {
        fun fromName(value: String): ChristmasSoundStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CELESTA_LUXURY
    }
}

enum class HomeLayoutStyle(val title: String, val subtitle: String) {
    SPHERE("Esfera", "Anillo orbital minimalista con estado central"),
    CENTER_BUTTON("Botón Central", "Botón táctil sólido con pulso de conexión"),
    CLASSIC("Clásico", "Consola compacta con badge de protocolo");

    companion object {
        fun fromName(value: String): HomeLayoutStyle =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SPHERE
    }
}

enum class DnsProvider(val label: String, val primary: String, val secondary: String) {
    CLOUDFLARE("Cloudflare DNS", "1.1.1.1", "1.0.0.1"),
    GOOGLE("Google DNS", "8.8.8.8", "8.8.4.4"),
    QUAD9("Quad9 DNS", "9.9.9.9", "149.112.112.112");

    companion object {
        fun fromName(value: String): DnsProvider =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLOUDFLARE
    }
}

data class AppSettingsState(
    val isAccountRegistered: Boolean = false,
    val termsAccepted: Boolean = false,
    val userCountry: CountryRegion = CountryRegion.GUATEMALA,
    val userAlias: String = "jeffers_user",
    val deviceId: String = "0922cd8ca3dcfb39",
    val personalTunnelUsername: String = "",
    val personalTunnelPassword: String = "",
    val performanceMode: VpnPerformanceMode = VpnPerformanceMode.BALANCED,
    val showSpeedPanel: Boolean = true,
    val dnsProvider: DnsProvider = DnsProvider.CLOUDFLARE,
    val fullTunnel: Boolean = true,
    val autoReconnect: Boolean = false,
    val mtu: Int = 1400,
    val smartAlerts: Boolean = true,
    val homeLayoutStyle: HomeLayoutStyle = HomeLayoutStyle.SPHERE,
    val themePreset: ThemePreset = ThemePreset.CHRISTMAS_LUXURY,
    val backgroundEffect: BackgroundEffect = BackgroundEffect.CHRISTMAS_SNOW,
    val serverAnimation: ServerAnimationStyle = ServerAnimationStyle.GALAXY,
    val cardAlpha: Float = 0.84f,
    val drawerAlpha: Float = 0.92f,
    val buttonAlpha: Float = 1.0f,
    val christmasSoundsEnabled: Boolean = true,
    val christmasAmbientEnabled: Boolean = true,
    val christmasSoundStyle: ChristmasSoundStyle = ChristmasSoundStyle.CELESTA_LUXURY,
    val activeSubscription: ActiveSubscription = ActiveSubscription(),
    val adminPasswordHash: String? = null
)

enum class VpnConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class VpnLogEntry(
    val id: Long,
    val timestamp: String,
    val tag: String,
    val message: String,
    val level: LogLevel
)

data class VpnTelemetry(
    val status: VpnConnectionStatus = VpnConnectionStatus.DISCONNECTED,
    val activeProfileId: Long? = null,
    val activeProfileName: String = "VPSpremiumPRO • Tigo",
    val serverHost: String = "192.6.154.212",
    val serverPort: Int = 8080,
    val carrier: CarrierType = CarrierType.TIGO,
    val protocol: TunnelProtocol = TunnelProtocol.BHTTP,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxBytesPerSec: Long = 0L,
    val txBytesPerSec: Long = 0L,
    val durationSeconds: Long = 0L,
    val pingMs: Int? = null,
    val statusMessage: String? = null
)

enum class ServerStatus(val label: String) {
    CONNECTED("Conectado"),
    DISCONNECTED("Desconectado"),
    ERROR("Error")
}

data class ServerVerificationState(
    val profileId: Long,
    val status: ServerStatus = ServerStatus.DISCONNECTED,
    val isChecking: Boolean = false,
    val isReachable: Boolean? = null,
    val latencyMs: Int? = null,
    val activeChannelLabel: String? = null,
    val detail: String = "Sin verificar",
    val checkedAtMillis: Long? = null
)

data class PingTestResult(
    val profileId: Long,
    val isReachable: Boolean,
    val latencyMs: Int?,
    val httpBanner: String?,
    val errorDetail: String?,
    val verifiedChannel: String? = null
)

enum class DiagnosticCheckStatus {
    PASS,
    WARN,
    FAIL
}

data class NetworkDiagnosticCheck(
    val id: String,
    val title: String,
    val detail: String,
    val status: DiagnosticCheckStatus,
    val latencyMs: Int? = null
)

data class NetworkDiagnosticReport(
    val checkedAtMillis: Long = System.currentTimeMillis(),
    val summary: String,
    val recommendedAction: String,
    val overallStatus: DiagnosticCheckStatus,
    val checks: List<NetworkDiagnosticCheck>
)

data class TunnelMonitorSample(
    val timestampMillis: Long,
    val rxBytesPerSec: Long,
    val txBytesPerSec: Long,
    val pingMs: Int?,
    val dnsOkCount: Int,
    val tcpOkCount: Int,
    val errorCount: Int
)

data class TunnelDiagnosticsState(
    val tunnelMode: String = "Inactivo",
    val dnsOkCount: Int = 0,
    val dnsFailCount: Int = 0,
    val lastDnsDetail: String? = null,
    val tcpOkCount: Int = 0,
    val tcpFailCount: Int = 0,
    val lastTcpDetail: String? = null,
    val sshFailCount: Int = 0,
    val lastSshDetail: String? = null,
    val ipv6RejectedCount: Int = 0,
    val orphanRstCount: Int = 0,
    val activeTcpSessions: Int = 0,
    val activeUdpSessions: Int = 0
)

