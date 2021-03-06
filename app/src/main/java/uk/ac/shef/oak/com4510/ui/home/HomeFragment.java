package uk.ac.shef.oak.com4510.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import uk.ac.shef.oak.com451.R;
import uk.ac.shef.oak.com4510.database.Trip;
import java.util.ArrayList;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Home fragment code for the Home activity using the RecyclerView
 */

public class HomeFragment extends Fragment {

    private List<Trip> myTrips = new ArrayList<>();
    private HomeAdapter  mAdapter;
    private RecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private HomeViewModel homeViewModel;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        homeViewModel =
                ViewModelProviders.of(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        mRecyclerView = root.findViewById(R.id.list_recycler_view);
        // set up the RecyclerView
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);

        mAdapter = new HomeAdapter(myTrips);
        mRecyclerView.setAdapter(mAdapter);

        homeViewModel.getAllTrips().observe(this, new Observer<List<Trip>>() {
            @Override
            public void onChanged(@Nullable List<Trip> tripList) {
                Log.i("Trips: ", "changed");
                mAdapter.updateData(tripList);
            }
        });

        return root;
    }

}