package com.rivalzin.bettersearch.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class FirstBootLanguages {
    private static final Map<String, String> DEFAULT_CODES = new HashMap<>();
    private static final Set<String> SUPPORTED_CODES = new HashSet<>();

    static {
        String codes = "af_za ar_sa ast_es az_az ba_ru bar be_by bg_bg br_fr brb bs_ba ca_es cs_cz cv_cu cy_gb "
                + "da_dk de_de el_gr en_us eo_uy es_es esan et_ee eu_es fa_ir fi_fi fil_ph fo_fo fr_fr fra_de "
                + "fur_it fy_nl ga_ie gd_gb gl_es go_fr got_de hal_ua haw_us he_il hi_in hn_no hr_hr hu_hu hy_am "
                + "id_id ig_ng io_en is_is isv it_it ja_jp jbo_en ka_ge kk_kz kn_in ko_kr ksh kw_gb ky_kg la_la "
                + "lb_lu li_li lmo lo_la lt_lt lv_lv lzh mk_mk mn_mn ms_my mt_mt nah nds_de nl_nl nn_no no_no "
                + "oc_fr ovd pl_pl pls pt_pt qcb_es ro_ro ru_ru ry_ua sah_sah se_no sk_sk sl_si so_so sq_al "
                + "sr_sp sv_se sxu szl ta_in th_th tl_ph tok tr_tr tt_ru tzo_mx uk_ua uz_uz val_es vec_it "
                + "vi_vn vro yi_de yo_ng zh_cn";
        for (String code : codes.split(" ")) {
            int separator = code.indexOf('_');
            DEFAULT_CODES.put(separator < 0 ? code : code.substring(0, separator), code);
            SUPPORTED_CODES.add(code);
        }
        SUPPORTED_CODES.addAll(Arrays.asList("be_latn", "de_at", "de_ch", "en_au", "en_ca", "en_gb", "en_nz",
                "es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve", "fr_ca", "fr_ch", "nl_be", "pt_br",
                "sr_cs", "zh_hk", "zh_tw"));
    }

    private FirstBootLanguages() {
    }

    static List<String> forLocale(Locale locale) {
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        languages.add(computerLanguage(locale));
        languages.add("en_us");
        languages.add("es_es");
        languages.add("es_mx");
        languages.add("es_ar");
        return new ArrayList<>(languages);
    }

    private static String computerLanguage(Locale locale) {
        if (locale == null) {
            return "en_us";
        }
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        String region = locale.getCountry().toLowerCase(Locale.ROOT);
        String script = locale.getScript();
        if ("iw".equals(language)) {
            language = "he";
        } else if ("in".equals(language)) {
            language = "id";
        } else if ("ji".equals(language)) {
            language = "yi";
        } else if ("nb".equals(language)) {
            language = "no";
        }
        if ("no".equals(language) && "NY".equalsIgnoreCase(locale.getVariant())) {
            return "nn_no";
        }
        if ("zh".equals(language)) {
            if ("Hans".equalsIgnoreCase(script)) {
                return "zh_cn";
            }
            if ("hk".equals(region)) {
                return "zh_hk";
            }
            return "Hant".equalsIgnoreCase(script) || "tw".equals(region) || "mo".equals(region)
                    ? "zh_tw" : "zh_cn";
        }
        if ("sr".equals(language)) {
            return "Latn".equalsIgnoreCase(script) ? "sr_cs" : "sr_sp";
        }
        if ("be".equals(language) && "Latn".equalsIgnoreCase(script)) {
            return "be_latn";
        }
        if ("es".equals(language) && "419".equals(region)) {
            return "es_mx";
        }
        if ("en".equals(language) && "uk".equals(region)) {
            return "en_gb";
        }
        String regional = language + '_' + region;
        if (SUPPORTED_CODES.contains(regional)) {
            return regional;
        }
        String fallback = DEFAULT_CODES.get(language);
        return fallback == null ? "en_us" : fallback;
    }
}
