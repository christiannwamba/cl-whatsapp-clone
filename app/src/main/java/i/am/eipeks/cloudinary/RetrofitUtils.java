package i.am.eipeks.cloudinary;


import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitUtils {

    private static Retrofit retrofit;

    public static Retrofit getRetrofit(){
        if (retrofit != null){
            return retrofit;
        }
        retrofit = new Retrofit.Builder()
                .baseUrl(Constants.MESSAGE_ENDPOINT)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit;

    }
}
